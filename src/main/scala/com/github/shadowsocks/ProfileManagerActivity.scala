package com.github.shadowsocks

import java.nio.charset.Charset
import java.util.{Date, Locale, Random}
import java.io.{BufferedReader, BufferedWriter, File, IOException, InputStreamReader}
import java.net._

import android.app.{Activity, NotificationManager, PendingIntent, ProgressDialog, TaskStackBuilder}
import android.content._
import android.content.pm.PackageManager
import android.nfc.NfcAdapter.CreateNdefMessageCallback
import android.nfc.{NdefMessage, NdefRecord, NfcAdapter, NfcEvent}
import android.os._
import android.provider.{MediaStore, Settings}
import android.support.v7.app.{AlertDialog, AppCompatActivity}
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.support.v7.widget._
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback
import android.text.style.TextAppearanceSpan
import android.text.{SpannableStringBuilder, Spanned, TextUtils}
import android.view._
import android.widget.{Adapter, AdapterView, ArrayAdapter, CheckBox, CheckedTextView, CompoundButton, EditText, ImageView, LinearLayout, Switch, TextView, Toast}
import android.net.Uri
import android.support.design.widget.Snackbar
import com.github.clans.fab.{FloatingActionButton, FloatingActionMenu}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.SSRSub
import com.github.shadowsocks.utils.{Key, Parser, TrafficMonitor, Utils}
import com.github.shadowsocks.widget.UndoSnackbarManager
import com.github.shadowsocks.utils._
import com.github.shadowsocks.utils.CloseUtils._
import net.glxn.qrgen.android.QRCode
import java.lang.System.currentTimeMillis
import java.lang.Thread
import java.text.SimpleDateFormat

import android.util.{Base64, Log}
import android.content.DialogInterface._
import okhttp3._
import java.util.concurrent.TimeUnit

import android.preference.PreferenceManager
import android.widget.AdapterView.OnItemSelectedListener

import scala.collection.mutable.ArrayBuffer
import tun2socks.Tun2socks

import scala.language.implicitConversions
import Profile._
import android.support.v4.app.NotificationCompat
import com.github.shadowsocks.database.VmessAction.profile
import com.github.shadowsocks.services.{BgResultReceiver, GetResultCallBack, LatencyTestService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, Future}
import scala.collection.immutable.HashMap

object ProfileManagerActivity {
  // profiles count
  def getProfilesByGroup (groupName: String, is_sort: Boolean): List[Profile] = {
    val allGroup = app.getString(R.string.allgroups)
    return {(groupName, is_sort) match {
      case (`allGroup`, true) => app.profileManager.getAllProfilesByElapsed
      case (`allGroup`, false) => app.profileManager.getAllProfiles
      case (_, true) => app.profileManager.getAllProfilesByGroupOrderByElapse(groupName)
      case (_, false) => app.profileManager.getAllProfilesByGroup(groupName)
    }}.getOrElse(List.empty[Profile])
  }

  def countProfilesByGroup (groupName: String): Long = {
    val allGroup = app.getString(R.string.allgroups)
    val group = if (groupName == allGroup) None else Some(groupName)
    app.profileManager.countAllProfilesByGroup(group)
  }
}

// TODO: AndroidX
// todo: import export
final class ProfileManagerActivity extends AppCompatActivity with OnMenuItemClickListener with ServiceBoundContext
  with View.OnClickListener with CreateNdefMessageCallback {

  private final class ProfileViewHolder(val view: View) extends RecyclerView.ViewHolder(view)
    with View.OnClickListener with View.OnKeyListener {

    var item: Profile = _
    var hideServer = false
//    private val text = itemView.findViewById(android.R.id.text1).asInstanceOf[CheckedTextView]
    // profile name
    private val text1 = itemView.findViewById(android.R.id.text1).asInstanceOf[TextView]
    private val text2 = itemView.findViewById(android.R.id.text2).asInstanceOf[TextView]
    // trafic
    private val tvTraffic = itemView.findViewById(R.id.traffic).asInstanceOf[TextView]
    itemView.setOnClickListener(this)
    itemView.setOnKeyListener(this)

    {
      val shareBtn = itemView.findViewById(R.id.share).asInstanceOf[ImageView]
      shareBtn.setOnClickListener(_ => {
        val url = item.toString
        if (isNfcBeamEnabled) {
          nfcAdapter.setNdefPushMessageCallback(ProfileManagerActivity.this,ProfileManagerActivity.this)
          nfcShareItem = url.getBytes(Charset.forName("UTF-8"))
        }
        val image = new ImageView(ProfileManagerActivity.this)
        image.setLayoutParams(new LinearLayout.LayoutParams(-1, -1))
        val qrcode = QRCode.from(url)
          .withSize(Utils.dpToPx(ProfileManagerActivity.this, 250), Utils.dpToPx(ProfileManagerActivity.this, 250))
          .asInstanceOf[QRCode].bitmap()
        image.setImageBitmap(qrcode)

        val dialog = new AlertDialog.Builder(ProfileManagerActivity.this, R.style.Theme_Material_Dialog_Alert)
          .setCancelable(true)
          .setPositiveButton(R.string.close, null)
          .setNegativeButton(R.string.copy_url, ((_, _) =>
            clipboard.setPrimaryClip(ClipData.newPlainText(null, url))): DialogInterface.OnClickListener)
          .setView(image)
          .setTitle(R.string.share)
          .create()
        if (!isNfcAvailable) dialog.setMessage(getString(R.string.share_message_without_nfc))
        else if (!isNfcBeamEnabled) {
          dialog.setMessage(getString(R.string.share_message_nfc_disabled))
          dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.turn_on_nfc),
            ((_, _) => startActivity(new Intent(Settings.ACTION_NFC_SETTINGS))): DialogInterface.OnClickListener)
        } else {
          dialog.setMessage(getString(R.string.share_message))
          dialog.setOnDismissListener(_ =>
            nfcAdapter.setNdefPushMessageCallback(null, ProfileManagerActivity.this))
        }
        dialog.show()
      })
      shareBtn.setOnLongClickListener(_ => {
        Utils.positionToast(Toast.makeText(ProfileManagerActivity.this, R.string.share, Toast.LENGTH_SHORT), shareBtn,
          getWindow, 0, Utils.dpToPx(ProfileManagerActivity.this, 8)).show
        true
      })
    }

    {
      val pingBtn = itemView.findViewById(R.id.ping_single).asInstanceOf[ImageView]
      pingBtn.setOnClickListener(_ => {

        getWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val singleTestProgressDialog = ProgressDialog.show(ProfileManagerActivity.this, getString(R.string.tips_testing), getString(R.string.tips_testing), false, true)
        item.pingItem(app.settings.getString(Key.PING_METHOD, "google")).foreach(result => {
            item.elapsed = result.data
            app.profileManager.updateProfile(item)
            this.updateText(0, 0, result.data)
            runOnUiThread(() => getWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON))
            singleTestProgressDialog.dismiss()
            Snackbar.make(findViewById(android.R.id.content), result.msg, Snackbar.LENGTH_LONG).show
          })
        // Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
      })
      pingBtn.setOnLongClickListener(_ => {
        Utils.positionToast(Toast.makeText(ProfileManagerActivity.this, R.string.ping, Toast.LENGTH_SHORT), pingBtn,
          getWindow, 0, Utils.dpToPx(ProfileManagerActivity.this, 8)).show
        true
      })
    }

    def updateText(txTotal: Long = 0, rxTotal: Long = 0, elapsedInput: Long = -1) {
      val tx = item.tx + txTotal
      val rx = item.rx + rxTotal
      val elapsed = if (elapsedInput != -1) elapsedInput else item.elapsed
      val trafficStatus = if (tx != 0 || rx != 0 || elapsed != 0 || item.url_group != "") {
        getString(R.string.stat_profiles,
          TrafficMonitor.formatTraffic(tx), TrafficMonitor.formatTraffic(rx), String.valueOf(elapsed), "").trim
      } else ""
      handler.post(() => {
        text1.setText(item.name)
        val serverAddress = if(item.isV2Ray) item.v_add else item.host
        text2.setText(if (hideServer) item.url_group else serverAddress )
        tvTraffic.setText(trafficStatus)
      })
    }

    def bind(item: Profile) {
      this.item = item
      updateText()
      if (item.id == app.profileId) {
        itemView.setSelected(true)
        selectedItem = this
      } else {
        itemView.setSelected(false)
        if (selectedItem eq this) selectedItem = null
      }
    }

    def onClick(v: View) {
      app.switchProfile(item.id)
      finish
    }

    def onKey(v: View, keyCode: Int, event: KeyEvent) = if (event.getAction == KeyEvent.ACTION_DOWN) keyCode match {
      case KeyEvent.KEYCODE_DPAD_LEFT =>
        val index = getAdapterPosition
        if (index >= 0) {
          profilesAdapter.remove(index)
          undoManager.remove(index, item)
          true
        } else false
      case _ => false
    } else false
  }

  // TODO: update adapter
  private class ProfilesAdapter extends RecyclerView.Adapter[ProfileViewHolder] {
    var profiles = new ArrayBuffer[Profile]
    var hideServer = false
    profiles ++= getProfilesByGroup(currentGroupName)

    def onGroupChange(groupName: String): Unit = {
      profiles = new ArrayBuffer[Profile]
      profiles ++= getProfilesByGroup(groupName)
      notifyDataSetChanged()
    }

    def getProfilesByGroup (groupName: String): List[Profile] = {
      val undoProfileIds = getUndoProfileIds
      ProfileManagerActivity.getProfilesByGroup(groupName, is_sort)
        .filter(p => !undoProfileIds.contains(p.id))
    }

    def getUndoProfileIds = Option(undoManager).map(_.getUndoItems.map(_._2.id).toList).getOrElse(List())

    def getItemCount = profiles.length

    def onBindViewHolder(vh: ProfileViewHolder, i: Int) = {
      vh.hideServer = hideServer
      vh.bind(profiles(i))
    }

    def onCreateViewHolder(vg: ViewGroup, i: Int) ={
      val layoutId = if (Build.VERSION.SDK_INT >= 21) R.layout.layout_profiles_item1 else R.layout.layout_profiles_item
      hideServer = app.settings.getBoolean(Key.HIDE_SERVER, false)
      new ProfileViewHolder(LayoutInflater.from(vg.getContext).inflate(layoutId, vg, false))
    }

    def resetProfiles (): Unit = {
      profilesAdapter.hideServer = app.settings.getBoolean(Key.HIDE_SERVER, false)
      is_sort = app.settings.getString(Key.SORT_METHOD, Key.SORT_METHOD_DEFAULT) == Key.SORT_METHOD_ELAPSED
      profiles.clear()
      profiles ++= getProfilesByGroup(currentGroupName)
    }

    def add(item: Profile) {
      undoManager.flush
      val pos = getItemCount
      profiles += item
      handler.post(() => {
        if (item.url_group != currentGroupName) initGroupSpinner(Some(item.url_group))
        else groupAdapter.notifyDataSetChanged()
        notifyItemInserted(pos)
      })
    }

    def move(from: Int, to: Int) {
      undoManager.flush
      val step = if (from < to) 1 else -1
      val first = profiles(from)
      var previousOrder = profiles(from).userOrder
      for (i <- from until to by step) {
        val next = profiles(i + step)
        val order = next.userOrder
        next.userOrder = previousOrder
        previousOrder = order
        profiles(i) = next
        app.profileManager.updateProfile(next)
      }
      first.userOrder = previousOrder
      profiles(to) = first
      app.profileManager.updateProfile(first)
      notifyItemMoved(from, to)
    }

    private def updateGroupSpinner (bypassGroupName: Option[String] = None): Unit = {
      if (profiles.isEmpty) initGroupSpinner(None, bypassGroupName)
      else if (currentGroupName == getString(R.string.allgroups) && bypassGroupName.isEmpty) initGroupSpinner()
      else groupAdapter.notifyDataSetChanged()
    }

    def remove(pos: Int) {
      profiles.remove(pos)
      notifyItemRemoved(pos)
      updateGroupSpinner(Option(currentGroupName))
    }
    def undo(actions: Iterator[(Int, Profile)]) = for ((index, item) <- actions) {
      profiles.insert(index, item)
      notifyItemInserted(index)
      updateGroupSpinner()
    }
    def commit(actions: Iterator[(Int, Profile)]) = {
      for ((index, item) <- actions) {
        app.profileManager.delProfile(item.id)
        if (item.id == app.profileId) app.profileId(-1)
      }
      updateGroupSpinner()
    }
    def updateByIds (ids: List[Int]): Unit = {
      val maps = app.profileManager.getProfileElapsed(ids) match {
        case Some(x) => x.map(p => (p.id, p.elapsed)).toMap
        case None => new HashMap[Int, Long]()
      }
      Log.e(TAG, s"updateByIds: ${maps.mkString(", ")}")
      profiles.zipWithIndex.foreach{
        case (p, i) => {
          val elapsed = maps.get(p.id)
          if (elapsed.nonEmpty) {
            p.elapsed = elapsed.getOrElse(p.elapsed)
            notifyItemChanged(i)
          }
        }
      }
    }
  }

  private final class SSRSubViewHolder(val view: View) extends RecyclerView.ViewHolder(view)
    with View.OnClickListener with View.OnKeyListener {

    var item: SSRSub = _
    private val text = itemView.findViewById(android.R.id.text2).asInstanceOf[TextView]
    itemView.setOnClickListener(this)

    def updateText(isShowUrl: Boolean = false) {
      val builder = new SpannableStringBuilder
      builder.append(this.item.url_group + "\n")
      if (isShowUrl) {
        val start = builder.length
        builder.append(this.item.url)
        builder.setSpan(new TextAppearanceSpan(ProfileManagerActivity.this, android.R.style.TextAppearance_Small),
          start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
      handler.post(() => text.setText(builder))
    }

    def bind(item: SSRSub) {
      this.item = item
      updateText()
    }

    def onClick(v: View) = {
      updateText(true)
    }

    def onKey(v: View, keyCode: Int, event: KeyEvent) = {
      true
    }
  }

  private class SSRSubAdapter extends RecyclerView.Adapter[SSRSubViewHolder] {
    var profiles = new ArrayBuffer[SSRSub]
    profiles ++= app.ssrsubManager.getAllSSRSubs.getOrElse(List.empty[SSRSub])

    def getItemCount = profiles.length

    def onBindViewHolder(vh: SSRSubViewHolder, i: Int) = vh.bind(profiles(i))

    def onCreateViewHolder(vg: ViewGroup, i: Int) =
      new SSRSubViewHolder(LayoutInflater.from(vg.getContext).inflate(R.layout.layout_ssr_sub_item, vg, false))

    def add(item: SSRSub) {
      undoManager.flush
      val pos = getItemCount
      profiles += item
      notifyItemInserted(pos)
    }

    def remove(pos: Int) {
      profiles.remove(pos)
      notifyItemRemoved(pos)
    }
  }

  private class GroupAdapter(context: Context, resID: Int) extends ArrayAdapter[String](context, resID) {


    def getCustomView (position: Int, convertView: View, parent: ViewGroup): View = {
      val layout = getLayoutInflater.inflate(R.layout.layout_group_spinner_item, parent, false)
      val tv1 = layout.findViewById(android.R.id.text1).asInstanceOf[TextView]
      val tv2: TextView = layout.findViewById(android.R.id.text2).asInstanceOf[TextView]
      val groupName = getItem(position)
      tv1.setText(groupName)
      if (groupName == currentGroupName) {
        val count = ProfileManagerActivity.countProfilesByGroup(currentGroupName)
        val txtCount = if (undoManager.getCount() > 0) {
          count - undoManager.getUndoItems.count(_._2.url_group == currentGroupName)
        } else count
        tv2.setText(s"${if (txtCount > 0) txtCount else ""}")
      } else {
        tv2.setText("")
      }
      layout
    }

    override def getDropDownView(position: Int, convertView: View, parent: ViewGroup): View = getCustomView(position, convertView, parent)

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = getCustomView(position, convertView, parent)
  }

  private var selectedItem: ProfileViewHolder = _
  private val handler = new Handler

  private var menu : FloatingActionMenu = _

  private lazy val profilesAdapter = new ProfilesAdapter
  private lazy val ssrsubAdapter = new SSRSubAdapter
  private var undoManager: UndoSnackbarManager[Profile] = _
  private lazy val groupAdapter = new GroupAdapter(this, R.layout.layout_group_spinner_item)

  private lazy val clipboard = getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
  private lazy val bgResultReceiver = new BgResultReceiver(new Handler(), (code: Int, bundle: Bundle) => {
    if (code == 100) {
      profilesAdapter.resetProfiles()
      profilesAdapter.notifyDataSetChanged()
      Toast.makeText(this, getString(R.string.action_full_test_finished), Toast.LENGTH_SHORT).show
    }
    if (code == 101) {
      val ids = bundle.getIntegerArrayList(Key.TEST_PROFILE_IDS)
      import scala.collection.JavaConversions._
      profilesAdapter.updateByIds(ids.toList.map(_.toInt))
    }
  })

  private var nfcAdapter : NfcAdapter = _
  private var nfcShareItem: Array[Byte] = _
  private var isNfcAvailable: Boolean = _
  private var isNfcEnabled: Boolean = _
  private var isNfcBeamEnabled: Boolean = _

  private var testProgressDialog: ProgressDialog = _
  private var testAsyncJob: Thread = _
  private var isTesting: Boolean = true
  private var ssTestProcess: GuardedProcess = _

  private val REQUEST_QRCODE = 1
  private var is_sort: Boolean = false
  private final val REQUEST_CREATE_DOCUMENT = 40
  private final val REQUEST_IMPORT_PROFILES = 41
  private final val REQUEST_IMPORT_QRCODE_IMAGE = 42
  private final val REQUEST_CONFIG_RESULT = 43
  private final val REQUEST_SETTINGS = 44
  private final val TAG = "ProfileManagerActivity"
  private var currentGroupName: String = _


  def isPortAvailable (port: Int):Boolean = {
    // Assume no connection is possible.
    var result = true;

    try {
      (new Socket("127.0.0.1", port)).close()
      result = false;
    } catch {
      case e: Exception => Unit
    }

    return result;
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    val action = getIntent().getAction()
    if (action != null && action.equals(Action.SCAN)) {
       qrcodeScan()
    }

    if (action != null && action.equals(Action.SORT)) {
       is_sort = true
    }

//    getWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
//    getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.layout_profiles)

    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.profiles)
    toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
    toolbar.setNavigationOnClickListener(_ => {
      val intent = getParentActivityIntent
      if (shouldUpRecreateTask(intent) || isTaskRoot)
        TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).startActivities()
      else finish()
    })
    toolbar.inflateMenu(R.menu.profile_manager_menu)
    toolbar.setOnMenuItemClickListener(this)
    val sortMethod = app.settings.getString(Key.SORT_METHOD, Key.SORT_METHOD_DEFAULT)
    val menuId = if (sortMethod == Key.SORT_METHOD_ELAPSED) R.id.action_sort_by_latency else R.id.action_sort_by_default
    toolbar.getMenu.findItem(menuId).setChecked(true)

    initFab()
    // get current group name
    initGroupSpinner(Some(app.settings.getString(Key.currentGroupName, getString(R.string.allgroups))))

    app.profileManager.setProfileAddedListener(profilesAdapter.add)
    val profilesList = findViewById(R.id.profilesList).asInstanceOf[RecyclerView]
    val layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false)
    profilesList.setLayoutManager(layoutManager)
    profilesList.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation))
    val animator = new DefaultItemAnimator
    animator.setSupportsChangeAnimations(false)
    profilesList.setItemAnimator(animator)
    profilesList.setAdapter(profilesAdapter)
    layoutManager.scrollToPosition(profilesAdapter.profiles.zipWithIndex.collectFirst {
      case (profile, i) if profile.id == app.profileId => i
    }.getOrElse(-1))
    undoManager = new UndoSnackbarManager[Profile](profilesList, profilesAdapter.undo, profilesAdapter.commit)
//    if (is_sort == false) {
    new ItemTouchHelper(new SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
      ItemTouchHelper.START) {

      def onSwiped(viewHolder: ViewHolder, direction: Int) = {
        val index = viewHolder.getAdapterPosition
        profilesAdapter.remove(index)
        undoManager.remove(index, viewHolder.asInstanceOf[ProfileViewHolder].item)
      }

      def onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder) = {
        profilesAdapter.move(viewHolder.getAdapterPosition, target.getAdapterPosition)
        true
      }
    }).attachToRecyclerView(profilesList)
//    }

    attachService(new IShadowsocksServiceCallback.Stub {
      def stateChanged(state: Int, profileName: String, msg: String) = () // ignore
      def trafficUpdated(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long) =
        if (selectedItem != null) selectedItem.updateText(txTotal, rxTotal)
    })

    if (app.settings.getBoolean(Key.profileTip, true)) {
      app.editor.putBoolean(Key.profileTip, false).apply
      new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert).setTitle(R.string.profile_manager_dialog)
        .setMessage(R.string.profile_manager_dialog_content).setPositiveButton(R.string.gotcha, null).create.show
    }

    val intent = getIntent
    if (intent != null) handleShareIntent(intent)
  }

  def initFab() {
    menu = findViewById(R.id.menu).asInstanceOf[FloatingActionMenu]
    menu.setClosedOnTouchOutside(true)
    val dm = AppCompatDrawableManager.get
    val manualAddFAB = findViewById(R.id.fab_manual_add).asInstanceOf[FloatingActionButton]
    manualAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_create))
    manualAddFAB.setOnClickListener(this)
    val qrcodeAddFAB = findViewById(R.id.fab_qrcode_add).asInstanceOf[FloatingActionButton]
    qrcodeAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_image_camera_alt))
    qrcodeAddFAB.setOnClickListener(this)
    val nfcAddFAB = findViewById(R.id.fab_nfc_add).asInstanceOf[FloatingActionButton]
    nfcAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_device_nfc))
    nfcAddFAB.setOnClickListener(this)
//    val importAddFAB = findViewById(R.id.fab_import_add).asInstanceOf[FloatingActionButton]
//    importAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_paste))
//    importAddFAB.setOnClickListener(this)
    val ssrsubAddFAB = findViewById(R.id.fab_ssr_sub).asInstanceOf[FloatingActionButton]
    ssrsubAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_rss))
    ssrsubAddFAB.setOnClickListener(this)
    menu.setOnMenuToggleListener(opened => if (opened) qrcodeAddFAB.setVisibility(
      if (getPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) View.VISIBLE else View.GONE))
  }

    // Add profiles counter
    def initGroupSpinner(groupName: Option[String] = None, ignoreGroupName: Option[String] = None ): Unit = {
      currentGroupName = groupName.getOrElse(getString(R.string.allgroups))
      val groupSpinner = findViewById(R.id.group_choose_spinner).asInstanceOf[AppCompatSpinner]
      groupAdapter.clear()
      val selectIndex = app.profileManager.getGroupNames match {
        case Some(groupNames) => {
          val allGroupNames = getString(R.string.allgroups) +: groupNames
          allGroupNames.filter(_ != ignoreGroupName.orNull).foreach(name => groupAdapter.add(name))
          Math.max(0, allGroupNames.indexOf(currentGroupName))
        }
        case None => 0
      }
      groupSpinner.setAdapter(groupAdapter)
      groupSpinner.setSelection(selectIndex)
      groupSpinner.setOnItemSelectedListener(new OnItemSelectedListener {
        def onNothingSelected(parent: AdapterView[_]): Unit = {}

        def onItemSelected(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
          currentGroupName = parent.getItemAtPosition(position).toString
          profilesAdapter.onGroupChange(currentGroupName)
          groupAdapter.notifyDataSetChanged()
          app.editor.putString(Key.currentGroupName, currentGroupName).apply()
        }
      })
  }


  override def onResume() {
    super.onResume()
    updateNfcState()
  }

  override def onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleShareIntent(intent)
  }

  def qrcodeScan() {
    try {
//        val intent = new Intent("com.google.zxing.client.android.SCAN")
//        intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
//
//        startActivityForResult(intent, 0)
      startActivity(new Intent(this, classOf[ScannerActivity]))
    } catch {
        case _ : Throwable =>
            if (menu != null) {
              menu.toggle(false)
            }
//            startActivity(new Intent(this, classOf[ScannerActivity]))
    }
  }

  override def onClick(v: View){
    v.getId match {
      case R.id.fab_manual_add =>
        menu.toggle(true)
        val profile = app.profileManager.createProfile()
        app.profileManager.updateProfile(profile)
        app.switchProfile(profile.id)
        finish
      case R.id.fab_qrcode_add =>
        menu.toggle(false)
        qrcodeScan()
      case R.id.fab_nfc_add =>
        menu.toggle(true)
        val dialog = new AlertDialog.Builder(ProfileManagerActivity.this, R.style.Theme_Material_Dialog_Alert)
          .setCancelable(true)
          .setPositiveButton(R.string.gotcha, null)
          .setTitle(R.string.add_profile_nfc_hint_title)
          .create()
        if (!isNfcBeamEnabled) {
          dialog.setMessage(getString(R.string.share_message_nfc_disabled))
          dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.turn_on_nfc), ((_, _) =>
              startActivity(new Intent(Settings.ACTION_NFC_SETTINGS))
            ): DialogInterface.OnClickListener)
        } else {
          dialog.setMessage(getString(R.string.add_profile_nfc_hint))
        }
        dialog.show
//      case R.id.fab_import_add =>
//        menu.toggle(true)
//        if (clipboard.hasPrimaryClip) {
//          val profiles_normal = Parser.findAll(clipboard.getPrimaryClip.getItemAt(0).getText).toList
//          val profiles_ssr = Parser.findAll_ssr(clipboard.getPrimaryClip.getItemAt(0).getText).toList
//          val profiles = profiles_normal ::: profiles_ssr
//          if (profiles.nonEmpty) {
//            val dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
//              .setTitle(R.string.add_profile_dialog)
//              .setPositiveButton(android.R.string.yes, ((_, _) =>
//                profiles.foreach(app.profileManager.createProfile)): DialogInterface.OnClickListener)
//              .setNeutralButton(R.string.dr, ((_, _) =>
//                profiles.foreach(app.profileManager.createProfile_dr)): DialogInterface.OnClickListener)
//              .setNegativeButton(android.R.string.no, ((_, _) => finish()): DialogInterface.OnClickListener)
//              .setMessage(profiles.mkString("\n"))
//              .create()
//            dialog.show()
//            return
//          }
//        }
//        Toast.makeText(this, R.string.action_import_err, Toast.LENGTH_SHORT).show
      case R.id.fab_ssr_sub =>
        menu.toggle(true)
//        ssrsubDialog()
        val intent = new Intent(this, classOf[ConfigActivity])
        intent.putExtra(Key.FRAGMENT_NAME, Key.FRAGMENT_SUBSCRIPTION)
        startActivityForResult(intent, REQUEST_CONFIG_RESULT)
    }
  }

  def ssrsubDialog() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    val view = View.inflate(this, R.layout.layout_ssr_sub, null);
    val sw_ssr_sub_autoupdate_enable = view.findViewById(R.id.sw_ssr_sub_autoupdate_enable).asInstanceOf[Switch]

    app.ssrsubManager.setSSRSubAddedListener(ssrsubAdapter.add)
    val ssusubsList = view.findViewById(R.id.ssrsubList).asInstanceOf[RecyclerView]
    val layoutManager = new LinearLayoutManager(this)
    ssusubsList.setLayoutManager(layoutManager)
    ssusubsList.setItemAnimator(new DefaultItemAnimator)
    ssusubsList.setAdapter(ssrsubAdapter)
    new ItemTouchHelper(new SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
      ItemTouchHelper.START | ItemTouchHelper.END) {
      def onSwiped(viewHolder: ViewHolder, direction: Int) = {
        val index = viewHolder.getAdapterPosition
        new AlertDialog.Builder(ProfileManagerActivity.this)
          .setTitle(getString(R.string.ssrsub_remove_tip_title))
          .setPositiveButton(R.string.ssrsub_remove_tip_direct, ((_, _) => {
            ssrsubAdapter.remove(index)
            app.ssrsubManager.delSSRSub(viewHolder.asInstanceOf[SSRSubViewHolder].item.id)
          }): DialogInterface.OnClickListener)
          .setNegativeButton(android.R.string.no,  ((_, _) => {
            ssrsubAdapter.notifyDataSetChanged()
          }): DialogInterface.OnClickListener)
          .setNeutralButton(R.string.ssrsub_remove_tip_delete,  ((_, _) => {
            val ssrsubItem = viewHolder.asInstanceOf[SSRSubViewHolder].item
            val delete_profiles = app.profileManager.getAllProfilesByGroup(ssrsubItem.url_group) match {
              case Some(profiles) =>
                profiles.filter(profile=> profile.ssrsub_id <= 0 || profile.ssrsub_id == ssrsubItem.id)
              case _ => null
            }

            delete_profiles.foreach((profile: Profile) => {
              if (profile.id != app.profileId) {
                app.profileManager.delProfile(profile.id)
              }
            })

            val index = viewHolder.getAdapterPosition
            ssrsubAdapter.remove(index)
            app.ssrsubManager.delSSRSub(viewHolder.asInstanceOf[SSRSubViewHolder].item.id)

            finish()
            startActivity(new Intent(getIntent()))
          }): DialogInterface.OnClickListener)
          .setMessage(getString(R.string.ssrsub_remove_tip))
          .setCancelable(false)
          .create()
          .show()
      }
      def onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder) = {
        true
      }
    }).attachToRecyclerView(ssusubsList)

    if (prefs.getInt(Key.ssrsub_autoupdate, 0) == 1) {
      sw_ssr_sub_autoupdate_enable.setChecked(true)
    }

    // auto update
    sw_ssr_sub_autoupdate_enable.setOnCheckedChangeListener(((_, isChecked: Boolean) => {
      val prefs_edit = prefs.edit()
      if (isChecked) {
        prefs_edit.putInt(Key.ssrsub_autoupdate, 1)
      } else {
        prefs_edit.putInt(Key.ssrsub_autoupdate, 0)
      }
      prefs_edit.apply()
    }): CompoundButton.OnCheckedChangeListener)

    // update subscription
    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.add_profile_methods_ssr_sub))
      .setPositiveButton(R.string.ssrsub_ok, ((_, _) => {
        Utils.ThrowableFuture {
          handler.post(() => {
            testProgressDialog = ProgressDialog.show(ProfileManagerActivity.this, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)
          })
          app.ssrsubManager.getAllSSRSubs match {
            case Some(ssrsubs) =>
              ssrsubs.foreach((ssrsub: SSRSub) => {
                  var delete_profiles = app.profileManager.getAllProfilesByGroup(ssrsub.url_group) match {
                    case Some(profiles) =>
                      profiles.filter(profile=> profile.ssrsub_id <= 0 || profile.ssrsub_id == ssrsub.id)
                    case _ => null
                  }
                  var result = ""
                  val builder = new OkHttpClient.Builder()
                                  .connectTimeout(5, TimeUnit.SECONDS)
                                  .writeTimeout(5, TimeUnit.SECONDS)
                                  .readTimeout(5, TimeUnit.SECONDS)

                  val client = builder.build();

                  val request = new Request.Builder()
                    .url(ssrsub.url)
                    .build();

                  try {
                    val response = client.newCall(request).execute()
                    val code = response.code()
                    if (code == 200) {
                      //
//                      val response_string = new String(Base64.decode(response.body().string, Base64.URL_SAFE))
                      val response_string = SSRSub.getResponseString(response)
                      var limit_num = -1
                      var encounter_num = 0
                      if (response_string.indexOf("MAX=") == 0) {
                        limit_num = response_string.split("\\n")(0).split("MAX=")(1).replaceAll("\\D+","").toInt
                      }
                      var profiles_ssr = Parser.findAll_ssr(response_string)
                      if (response_string.indexOf("MAX=") == 0) {
                        profiles_ssr = scala.util.Random.shuffle(profiles_ssr)
                      }
                      val profiles_vmess = Parser.findAllVmess(response_string)
                        .map(profile => {
                          profile.url_group = ssrsub.url_group
                          profile
                        })
                      val profiles = profiles_ssr ++ profiles_vmess
                      profiles.foreach((profile: Profile) => {
                        if (encounter_num < limit_num && limit_num != -1 || limit_num == -1) {
                          profile.ssrsub_id = ssrsub.id
                          val result = app.profileManager.createProfile_sub(profile)
                          if (result != 0) {
                            delete_profiles = delete_profiles.filter(_.id != result)
                          }
                        }
                        encounter_num += 1
                      })

                      delete_profiles.foreach((profile: Profile) => {
                        if (profile.id != app.profileId) {
                          app.profileManager.delProfile(profile.id)
                        }
                      })
                    } else throw new Exception(getString(R.string.ssrsub_error, code: Integer))
                    response.body().close()
                  } catch {
                    case e: IOException =>
                      result = getString(R.string.ssrsub_error, e.getMessage)
                  }
              })
            case _ => Toast.makeText(this, R.string.action_export_err, Toast.LENGTH_SHORT).show
          }

          handler.post(() => testProgressDialog.dismiss)

          finish()
          startActivity(new Intent(getIntent()))
        }
      }): DialogInterface.OnClickListener)
      .setNegativeButton(android.R.string.no, null)
      .setNeutralButton(R.string.ssrsub_add, ((_, _) => {
        // add sub url
        val UrlAddEdit = new EditText(this);
        new AlertDialog.Builder(this)
          .setTitle(getString(R.string.ssrsub_add))
          .setPositiveButton(android.R.string.ok, ((_, _) => {
            if(UrlAddEdit.getText().toString() != "") {
              Utils.ThrowableFuture {
                handler.post(() => {
                  testProgressDialog = ProgressDialog.show(ProfileManagerActivity.this, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)
                })
                var result = ""
                val builder = new OkHttpClient.Builder()
                                .connectTimeout(5, TimeUnit.SECONDS)
                                .writeTimeout(5, TimeUnit.SECONDS)
                                .readTimeout(5, TimeUnit.SECONDS)

                val client = builder.build();

                try {
                  val request = new Request.Builder()
                    .url(UrlAddEdit.getText().toString())
                    .build();
                  val response = client.newCall(request).execute()
                  val code = response.code()
                  if (code == 200) {
                    val responseString = SSRSub.getResponseString(response)
                    SSRSub.createSSRSub(responseString, response.request().url().toString) match {
                      case Some(ssrsub) => handler.post(() => app.ssrsubManager.createSSRSub(ssrsub))
                      case None =>
                    }
                  } else throw new Exception(getString(R.string.ssrsub_error, code: Integer))
                  response.body().close()
                } catch {
                  case e: Exception =>
                    e.printStackTrace()
                    result = getString(R.string.ssrsub_error, e.getMessage)
                }
                handler.post(() => testProgressDialog.dismiss)
                handler.post(() => ssrsubDialog())
              }
            } else {
              handler.post(() => ssrsubDialog())
            }
          }): DialogInterface.OnClickListener)
          .setNegativeButton(android.R.string.no, ((_, _) => {
            ssrsubDialog()
          }): DialogInterface.OnClickListener)
          .setView(UrlAddEdit)
          .create()
          .show()
      }): DialogInterface.OnClickListener)
      .setView(view)
      .create()
      .show()
  }

  def updateNfcState() {
    isNfcAvailable = false
    isNfcEnabled = false
    isNfcBeamEnabled = false
    nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    if (nfcAdapter != null) {
      isNfcAvailable = true
      if (nfcAdapter.isEnabled) {
        isNfcEnabled = true
        if (nfcAdapter.isNdefPushEnabled) {
          isNfcBeamEnabled = true
          nfcAdapter.setNdefPushMessageCallback(null, ProfileManagerActivity.this)
        }
      }
    }
  }

  def handleShareIntent(intent: Intent) {
    val sharedStr = intent.getAction match {
      case Intent.ACTION_VIEW => intent.getData.toString
      case NfcAdapter.ACTION_NDEF_DISCOVERED =>
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMsgs != null && rawMsgs.nonEmpty)
          new String(rawMsgs(0).asInstanceOf[NdefMessage].getRecords()(0).getPayload)
        else null
      case _ => null
    }

    if (TextUtils.isEmpty(sharedStr)) return
    val profiles_normal = Parser.findAll(sharedStr).toList
    val profiles_ssr = Parser.findAll_ssr(sharedStr).toList
    val profiles = profiles_ssr ::: profiles_normal
    if (profiles.isEmpty) {
      finish()
      return
    }
    val dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
      .setTitle(R.string.add_profile_dialog)
      .setPositiveButton(android.R.string.yes, ((_, _) =>
        profiles.foreach(app.profileManager.createProfile)): DialogInterface.OnClickListener)
      .setNeutralButton(R.string.dr, ((_, _) =>
        profiles.foreach(app.profileManager.createProfile_dr)): DialogInterface.OnClickListener)
      .setNegativeButton(android.R.string.no, ((_, _) => finish()): DialogInterface.OnClickListener)
      .setMessage(profiles.mkString("\n"))
      .create()
    dialog.show()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    if (requestCode == 0) {
      if (resultCode == Activity.RESULT_OK) {
        val contents = data.getStringExtra("SCAN_RESULT")
        createProfilesFromText(contents)
      }
      if (resultCode == Activity.RESULT_CANCELED) {
        Toast.makeText(this, "Fail to Scan QRCode", Toast.LENGTH_SHORT)
      }
      return
    }
    if (resultCode != Activity.RESULT_OK) return
    requestCode match {
      case REQUEST_CREATE_DOCUMENT => {
        autoClose({
          val filePath = data.getData
          getContentResolver.openOutputStream(filePath)
        })(out => {
          val profiles = ProfileManagerActivity.getProfilesByGroup(currentGroupName, false)
          val buffer = profiles.mkString("\n").getBytes(Charset.forName("UTF-8"))
          out.write(buffer)
          Toast.makeText(this, R.string.action_export_file_msg, Toast.LENGTH_SHORT).show
//          app.profileManager.getAllProfiles match {
//            case Some(profiles) =>
//              val buffer = profiles.mkString("\n").getBytes(Charset.forName("UTF-8"))
//              out.write(buffer)
//              Toast.makeText(this, R.string.action_export_file_msg, Toast.LENGTH_SHORT).show
//            case _ => Toast.makeText(this, R.string.action_export_file_err, Toast.LENGTH_SHORT).show
//          }
        })
      }
      case REQUEST_IMPORT_PROFILES => {
        autoClose(getContentResolver.openInputStream(data.getData))(in => {
          val lines = scala.io.Source.fromInputStream(in).mkString
          val profiles_ssr = Parser.findAll_ssr(lines).toList
          val profiles_v2ray = Parser.findAllVmess(lines).toList
          val profiles = profiles_ssr ::: profiles_v2ray
          profiles.foreach(app.profileManager.createProfile)
        })
      }
      case REQUEST_CONFIG_RESULT => {
        val groupName = Option(data.getStringExtra(Key.SUBSCRIPTION_GROUP_NAME))
        undoManager.flush
        if (groupName.getOrElse("") != currentGroupName) initGroupSpinner(groupName)
        else groupAdapter.notifyDataSetChanged()
        if (data.getStringExtra(Key.SUBSCRIPTION_UPDATED) == "true" && groupName.getOrElse("") == currentGroupName) profilesAdapter.resetProfiles()
        profilesAdapter.notifyDataSetChanged()
      }
      case REQUEST_SETTINGS => {
        profilesAdapter.resetProfiles()
        profilesAdapter.notifyDataSetChanged()
      }
      case REQUEST_IMPORT_QRCODE_IMAGE => {
        val uri = data.getData
        val bitmap = MediaStore.Images.Media.getBitmap(getContentResolver, uri)
        Utils.ThrowableFuture{
          QRCodeDecoder.syncDecodeQRCode(bitmap) match {
            case Some(url) => {
              Log.e(TAG, url)
              handler.post(() => {
                val dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
                  .setTitle(R.string.add_profile_dialog)
                  .setPositiveButton(android.R.string.yes, ((_, _) =>
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, url))): DialogInterface.OnClickListener)
                  .setNeutralButton(R.string.open_url, ((_, _) => openUrl(url)): DialogInterface.OnClickListener)
                  .setNegativeButton(android.R.string.no, ((_, _) => finish()): DialogInterface.OnClickListener)
                  .setMessage(url)
                  .create()
                dialog.show()
              })
            }
            case None => handler.post(() => {
              Toast.makeText(this, "Nothing Found", Toast.LENGTH_SHORT)
            })
          }
        }
      }
      case _ =>
    }
  }

  def openUrl (url: String): Unit ={
    if (android.util.Patterns.WEB_URL.matcher(url).find()) {
      val intent = new Intent(Intent.ACTION_VIEW)
      intent.setData(Uri.parse(url))
      startActivity(intent)
    }
      """(?i)ssr?://.*""".r.findFirstIn(url) match {
      case Some(x) => createProfilesFromText(url)
      case None =>
    }
  }

  def createProfilesFromText (contents: CharSequence): Boolean = {
    if (TextUtils.isEmpty(contents)) return false
    val profiles_normal = Parser.findAll(contents).toList
    val profiles_ssr = Parser.findAll_ssr(contents).toList
    val profiles_vmess = Parser.findAllVmess(contents).toList
    val profiles = profiles_ssr ::: profiles_normal ::: profiles_vmess
    if (profiles.isEmpty) {
      finish()
      return false
    }
    val dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
      .setTitle(R.string.add_profile_dialog)
      .setPositiveButton(android.R.string.yes, ((_, _) =>
        profiles.foreach(app.profileManager.createProfile)): DialogInterface.OnClickListener)
      .setNeutralButton(R.string.dr, ((_, _) =>
        profiles.foreach(app.profileManager.createProfile_dr)): DialogInterface.OnClickListener)
      .setNegativeButton(android.R.string.no, ((_, _) => finish()): DialogInterface.OnClickListener)
      .setMessage(profiles.mkString("\n"))
      .create()
    dialog.show()
    true
  }

  override def onStart() {
    super.onStart()
    registerCallback
  }
  override def onStop() {
    super.onStop()
    unregisterCallback
  }

  override def onDestroy {
    detachService()

    if (ssTestProcess != null) {
      ssTestProcess.destroy()
      ssTestProcess = null
    }

    undoManager.flush
    app.profileManager.setProfileAddedListener(null)
    super.onDestroy
  }

  override def onBackPressed() {
    if (menu.isOpened) menu.close(true) else super.onBackPressed()
  }

  def createNdefMessage(nfcEvent: NfcEvent) =
    new NdefMessage(Array(new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, nfcShareItem, Array[Byte](), nfcShareItem)))

  val showProgresshandler = new Handler(Looper.getMainLooper()) {
    override def handleMessage(msg: Message) {
      val message = msg.obj.asInstanceOf[String]
      if (testProgressDialog != null) {
        testProgressDialog.setMessage(message)
      }
    }
  }

  def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
    case R.id.action_export =>
      val profiles = ProfileManagerActivity.getProfilesByGroup(currentGroupName, false)
      clipboard.setPrimaryClip(ClipData.newPlainText(null, profiles.mkString("\n")))
      Toast.makeText(this, R.string.action_export_msg, Toast.LENGTH_SHORT).show
//      app.profileManager.getAllProfiles match {
//        case Some(profiles) =>
//          clipboard.setPrimaryClip(ClipData.newPlainText(null, profiles.mkString("\n")))
//          Toast.makeText(this, R.string.action_export_msg, Toast.LENGTH_SHORT).show
//        case _ => Toast.makeText(this, R.string.action_export_err, Toast.LENGTH_SHORT).show
//      }
      true
    case R.id.action_import_clipboard =>
      if (clipboard.hasPrimaryClip) {
        val link = clipboard.getPrimaryClip.getItemAt(0).getText
        if (createProfilesFromText(link)) return true
      }
      Toast.makeText(this, R.string.action_import_err, Toast.LENGTH_SHORT).show
      true
      // startFilesForResult
    case R.id.action_export_file =>
      val dateFormat = new SimpleDateFormat("yyyyMMddhhmmss")
      val date = dateFormat.format(new Date())
      val intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
      intent.addCategory(Intent.CATEGORY_OPENABLE)
      intent.setType("text/plain")
      intent.putExtra(Intent.EXTRA_TITLE, s"profiles-$date.txt")
      startActivityForResult(intent, REQUEST_CREATE_DOCUMENT)
      true
    case R.id.action_import_file =>
      val intent = new Intent(Intent.ACTION_GET_CONTENT)
      intent.setType("text/plain")
      intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
      intent.addCategory(Intent.CATEGORY_OPENABLE)
      startActivityForResult(Intent.createChooser(intent, "SSR"), REQUEST_IMPORT_PROFILES)
      true
    case R.id.action_import_qrcode_image =>
      val intent = new Intent(Intent.ACTION_GET_CONTENT)
      intent.setType("image/*")
      intent.putExtra(Intent.EXTRA_MIME_TYPES, "image/png")
      intent.putExtra(Intent.EXTRA_MIME_TYPES, "image/jpg")
      intent.putExtra(Intent.EXTRA_MIME_TYPES, "image/jpeg")
      intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
      intent.addCategory(Intent.CATEGORY_OPENABLE)
      startActivityForResult(Intent.createChooser(intent, "SSR"), REQUEST_IMPORT_QRCODE_IMAGE)
      true
//    case R.id.action_import_manually_vmess => {
//      startActivity(new Intent(this, classOf[NewProfileActivity]))
//      true
//    }
    case R.id.action_add_v2ray_config =>
      startActivity(new Intent(this, classOf[ConfigActivity]))
      true
    case R.id.action_full_test =>
//      if (app.settings.getBoolean(Key.FULL_TEST_BG, false)) {
      if (1 == 1) {
        val intent = new Intent(this, classOf[LatencyTestService])
        intent.putExtra(Key.currentGroupName, currentGroupName)
        intent.putExtra("BgResultReceiver", bgResultReceiver)
        intent.putExtra("is_sort", is_sort)
        startService(intent)
        return true
      }
      val testProfiles = if (currentGroupName == getString(R.string.allgroups)) app.profileManager.getAllProfiles
      else app.profileManager.getAllProfilesByGroup(currentGroupName)
      testProfiles match {
//      app.profileManager.getAllProfiles match {
        case Some(profiles) =>

          isTesting = true

          testProgressDialog = ProgressDialog.show(this, getString(R.string.tips_testing), getString(R.string.tips_testing), false, true, new OnCancelListener() {
              def onCancel(dialog: DialogInterface) {
                  // TODO Auto-generated method stub
                  // Do something...
                  if (testProgressDialog != null) {
                    testProgressDialog = null;
                  }

                  isTesting = false
                  testAsyncJob.interrupt()
                  runOnUiThread(() => getWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON))
                  finish()
                  startActivity(new Intent(getIntent))
              }
          })
          getWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

          val testV2rayProfiles = (v2rayProfiles: List[List[Profile]], size: Int) => {
            val pingMethod = app.settings.getString(Key.PING_METHOD, "google")
            v2rayProfiles.indices.foreach(index => {
              val profiles = v2rayProfiles(index)
              val futures = profiles.indices.map(i =>{
                val p = profiles(i)
                Future(p.pingItemThread(pingMethod, 8900L + index * size + i))
                  .map(testResult => {
                    val msg = Message.obtain()
                    msg.obj = s"${profile.name} $testResult"
                    msg.setTarget(showProgresshandler)
                    msg.sendToTarget()
                  })
              })
              // TODO: Duration
              Await.ready(Future.sequence(futures), Duration(20, SECONDS)).onFailure{
                case e: Exception => e.printStackTrace()
              }
            })
          }

          val testV2rayJob = (v2rayProfiles: List[Profile]) => {
            testV2rayProfiles(v2rayProfiles.grouped(4).toList, 4)
            val zeroV2RayProfiles = v2rayProfiles.filter(p => p.elapsed == 0 && p.isV2Ray)
            if (zeroV2RayProfiles.nonEmpty) {
              testV2rayProfiles(zeroV2RayProfiles.grouped(2).toList, 2)
            }
          }

          // TODO: refactor
          // connection pool time


          val testSSRProfiles = (ssrProfiles: List[List[Profile]], size: Int, offset: Int) => {
            ssrProfiles.indices.foreach(index => {
              val profiles: List[Profile] = ssrProfiles(index)
              try {
                val confServer = profiles.indices.map(i => {
                  val profile = profiles(i)
                  var host = profile.host
                  if (!Utils.isNumeric(host)) Utils.resolve(host, enableIPv6 = false) match {
                    case Some(addr) => host = addr
                    case None => host = "127.0.0.1"
                  }
                  ConfigUtils.SHADOWSOCKSR_TEST_SERVER.formatLocal(Locale.ENGLISH,
                    s"${host}${profile.remotePort}", host, profile.remotePort, profile.localPort + index * size + i + offset, ConfigUtils.EscapedJson(profile.password), profile.method,
                    profile.protocol, ConfigUtils.EscapedJson(profile.protocol_param), profile.obfs, ConfigUtils.EscapedJson(profile.obfs_param)
                  )
                }).mkString(",")
                val confTest = ConfigUtils.SHADOWSOCKSR_TEST_CONF.formatLocal(Locale.ENGLISH,
                  confServer, 600, "www.google.com:80")
                Utils.printToFile(new File(getApplicationInfo.dataDir + "/ss-local-test.conf"))(p => {
                  p.println(confTest)
                })

                val cmd = ArrayBuffer[String](Utils.getAbsPath(ExeNative.SS_LOCAL)
                  , "-t", "600"
                  , "-L", "www.google.com:80"
                  , "-c", getApplicationInfo.dataDir + "/ss-local-test.conf")

                if (TcpFastOpen.sendEnabled) cmd += "--fast-open"

                if (ssTestProcess != null) {
                  ssTestProcess.destroy()
                  ssTestProcess = null
                }

                ssTestProcess = new GuardedProcess(cmd).start()

                var start = 0
                while (start < 2 && isPortAvailable(profiles.head.localPort + index * size + offset)) {
                  try {
                    start = start + 1
                    Thread.sleep(50)
                  } catch {
                    case e: InterruptedException => isTesting = false
                  }
                }

                val futures = profiles.indices.map(i => Future {
                  var result = ""
                  val profile = profiles(i)
                  // TODO: batch test with go
                  val elapsed = Tun2socks.testURLLatency("http://127.0.0.1:" + (profile.localPort + index * size + i + offset) + "/generate_204")
                  result = getString(R.string.connection_test_available, elapsed: java.lang.Long)
                  profile.elapsed = elapsed
                  // Log.e(TAG, s"host:${profile.host}, elapsed: $elapsed")
                  app.profileManager.updateProfile(profile)
                  result
                }.recover {
                  case e: Exception => {
                    val profile = profiles(i)
                    Log.e(TAG, s"host: ${profile.host}, msg: ${e.getMessage}")
                    profile.elapsed = 0
                    app.profileManager.updateProfile(profile)
                    e.getMessage
                  }
                }.map(testResult => {
                  val profile = profiles(i)
                  val msg = Message.obtain()
                  msg.obj = s"${profile.name} $testResult"
                  msg.setTarget(showProgresshandler)
                  msg.sendToTarget()
                }))
                Await.ready(Future.sequence(futures), Duration(5 * size, SECONDS)).onFailure{
                  case e: Exception => e.printStackTrace()
                }
              } catch {
                case e: Exception => e.printStackTrace()
              }
              if (ssTestProcess != null) {
                ssTestProcess.destroy()
                ssTestProcess = null
              }
            })
          }

          val testTCPSSRProfiles = (ssrProfiles: List[List[Profile]], size: Int, offset: Int) => {
            ssrProfiles.indices.foreach(index => {
              val profiles: List[Profile] = ssrProfiles(index)
              val futures = profiles.map(p => Future {
                val testResult = p.testTCPLatencyThread()
                val msg = Message.obtain()
                msg.obj = s"${p.name} $testResult"
                msg.setTarget(showProgresshandler)
                msg.sendToTarget()
              })
              Await.ready(Future.sequence(futures), Duration(5 * size, SECONDS)).onFailure{
                case e: Exception => e.printStackTrace()
              }
            })
          }

          // TODO: Retry
          val testSSRJob = (ssrProfiles: List[Profile]) => {
            val pingMethod = app.settings.getString(Key.PING_METHOD, "google")
            val pingFunc = if (pingMethod == "google") testSSRProfiles else testTCPSSRProfiles
            pingFunc(ssrProfiles.grouped(4).toList, 4, ssrProfiles.size)
            val zeroSSRProfiles = ssrProfiles.filter(p => p.elapsed == 0 && !p.isV2Ray)
            if (zeroSSRProfiles.nonEmpty) {
              pingFunc(zeroSSRProfiles.grouped(2).toList, 2, ssrProfiles.size)
            }
          }
          testAsyncJob = new Thread {
            override def run() {
              // Do some background work
              Looper.prepare()
              val (v2rayProfiles, ssrProfiles) = profiles
                .filter(p => !List("www.google.com", "127.0.0.1", "8.8.8.8", "1.2.3.4", "1.1.1.1").contains(p.host))
                .partition(_.isV2Ray)
              if (v2rayProfiles.nonEmpty) { testV2rayJob(v2rayProfiles) }
              if (ssrProfiles.nonEmpty) { testSSRJob(ssrProfiles) }
              // end of test
              if (testProgressDialog != null) {
                testProgressDialog.dismiss
                testProgressDialog = null
              }

              runOnUiThread(() => getWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON))
              finish()
              startActivity(new Intent(Action.SORT))
              Looper.loop()
            }
          }

          testAsyncJob.start()

        case _ => Toast.makeText(this, R.string.action_export_err, Toast.LENGTH_SHORT).show
      }
      true
    case R.id.action_sort =>
      finish()
      val intent = new Intent(Action.SORT)
      startActivity(intent)
      true
    case R.id.action_batch_delete => {
      val dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
        .setTitle(getString(R.string.batch_delete))
        .setPositiveButton(android.R.string.yes, ((_, _) =>{
          ProfileManagerActivity.getProfilesByGroup(currentGroupName, false)
            .filter(_.id != app.profileId)
            .foreach(profile => app.profileManager.delProfile(profile.id))
          finish()
          startActivity(new Intent(getIntent()))
        }): DialogInterface.OnClickListener)
        .setNegativeButton(android.R.string.no, null)
        .setNeutralButton(R.string.delete_zero_latency,  ((_, _) => {
          ProfileManagerActivity.getProfilesByGroup(currentGroupName, false)
            .filter(p => p.elapsed < 1 && p.id != app.profileId )
            .foreach(profile => app.profileManager.delProfile(profile.id))
          finish()
          startActivity(new Intent(getIntent()))
        }): DialogInterface.OnClickListener)
        .setMessage(getString(R.string.batch_delete_msg, currentGroupName))
        .create()
      dialog.show()
      true
    }
    case R.id.action_settings => {
      startActivityForResult(new Intent(this, classOf[SettingActivity]), REQUEST_SETTINGS)
      true
    }
    case R.id.action_sort_by_default => {
      item.setChecked(true)
      app.settings.edit().putString(Key.SORT_METHOD, Key.SORT_METHOD_DEFAULT).apply()
      is_sort = false
      profilesAdapter.resetProfiles()
      profilesAdapter.notifyDataSetChanged()
      true
    }
    case R.id.action_sort_by_latency => {
      item.setChecked(true)
      app.settings.edit().putString(Key.SORT_METHOD, Key.SORT_METHOD_ELAPSED).apply()
      is_sort = true
      profilesAdapter.resetProfiles()
      profilesAdapter.notifyDataSetChanged()
      true
    }
    case _ => false
  }
}
