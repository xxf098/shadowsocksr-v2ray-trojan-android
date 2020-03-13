package com.github.shadowsocks.fragments

import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

import android.app.ProgressDialog
import android.content.{ClipData, ClipboardManager, Context, DialogInterface, Intent}
import android.os.{Bundle, Handler}
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback
import android.support.v7.widget.{DefaultItemAnimator, DividerItemDecoration, LinearLayoutManager, RecyclerView}
import android.text.style.TextAppearanceSpan
import android.text.{SpannableStringBuilder, Spanned, TextUtils}
import android.util.Log
import android.view.{KeyEvent, LayoutInflater, MenuItem, View, ViewGroup}
import android.widget.{CompoundButton, EditText, ImageView, PopupMenu, Switch, TextView, Toast}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.{Profile, SSRSub}
import com.github.shadowsocks.utils.{Key, Parser, Utils}
import com.github.shadowsocks.widget.UndoSnackbarManager
import com.github.shadowsocks.{ConfigActivity, ProfileManagerActivity, R}
import okhttp3.{OkHttpClient, Request}
import android.view.View
import android.webkit.URLUtil

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

// TODO: refactor
class SubscriptionFragment extends Fragment with OnMenuItemClickListener {

  private final val TAG = "SubscriptionFragment"
  private val handler = new Handler
  private lazy val ssrsubAdapter = new SSRSubAdapter
  private var testProgressDialog: ProgressDialog = _
  private lazy val configActivity = getActivity.asInstanceOf[ConfigActivity]
  private lazy val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.layout_subscriptions, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    val activity = getActivity.asInstanceOf[ConfigActivity]
    activity.toolbar.setTitle(R.string.subscriptions)
    activity.toolbar.inflateMenu(R.menu.subscription_menu)
    activity.toolbar.setOnMenuItemClickListener(this)

    app.ssrsubManager.setSSRSubAddedListener(ssrsubAdapter.add)

    // auto update
    val prefs = PreferenceManager.getDefaultSharedPreferences(configActivity)
    val sw_ssr_sub_autoupdate_enable = view.findViewById(R.id.sw_ssr_sub_autoupdate_enable).asInstanceOf[Switch]
    if (prefs.getInt(Key.ssrsub_autoupdate, 0) == 1) {
      sw_ssr_sub_autoupdate_enable.setChecked(true)
    }
    sw_ssr_sub_autoupdate_enable.setOnCheckedChangeListener(((_, isChecked: Boolean) => {
      val prefs_edit = prefs.edit()
      if (isChecked) {
        prefs_edit.putInt(Key.ssrsub_autoupdate, 1)
      } else {
        prefs_edit.putInt(Key.ssrsub_autoupdate, 0)
      }
      prefs_edit.commit()
    }): CompoundButton.OnCheckedChangeListener)

    val ssusubsList = view.findViewById(R.id.ssrsubList).asInstanceOf[RecyclerView]
    val layoutManager = new LinearLayoutManager(activity)
    ssusubsList.setLayoutManager(layoutManager)
    ssusubsList.addItemDecoration(new DividerItemDecoration(configActivity, layoutManager.getOrientation))
    ssusubsList.setItemAnimator(new DefaultItemAnimator)
    ssusubsList.setAdapter(ssrsubAdapter)
    setupRemoveSubscription(ssusubsList)
  }

  def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
    case R.id.action_add_subscription => {
      addSubscription()
      true
    }
    case R.id.action_update_subscription => {
      updateAllSubscriptions()
      true
    }
    case _ => false
  }

  private[this] def addSubscription(): Unit = {
    showSubscriptionDialog(None) { (responseString, url, groupName) => {
      SSRSub.createSSRSub(responseString, url, groupName) match {
        case Some(ssrsub) => {
          handler.post(() => app.ssrsubManager.createSSRSub(ssrsub))
//          addProfilesFromSubscription(ssrsub, responseString)
          ssrsub.addProfiles(responseString)
          notifyGroupNameChange(Some(ssrsub.url_group))
        }
        case None =>
      }
    }
    }
  }

  private  def showSubscriptionDialog (ssrSub: Option[SSRSub])(responseHandler: (String, String, String) => Unit): Unit = {
    val context = getActivity
    val view = View.inflate(context, R.layout.layout_ssr_sub_add, null)
    val etAddUrl = view.findViewById(R.id.et_subscription_url).asInstanceOf[EditText]
    val etGroupName = view.findViewById(R.id.et_group_name).asInstanceOf[EditText]
    var title = getString(R.string.ssrsub_add)
    ssrSub match {
      case Some(sub) => {
        etAddUrl.setText(sub.url)
        etGroupName.setText(sub.url_group)
        title = getString(R.string.ssrsub_edit)
      }
      case None =>
    }
    new AlertDialog.Builder(context)
      .setTitle(getString(R.string.ssrsub_add))
      .setPositiveButton(android.R.string.ok, ((_, _) => {
        val url = etAddUrl.getText.toString
        val groupName = etGroupName.getText.toString
        if(URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
          ssrSub match {
            case Some(x) if x.url == url => responseHandler(null, url, groupName)
            case _ => Utils.ThrowableFuture {
              handler.post(() => testProgressDialog = ProgressDialog.show(context, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true))
              SSRSub.getSubscriptionResponse(url).flatMap(responseString => Try{
                responseHandler(responseString, url, groupName)
                None
              }).recover{
                case e: Exception => Some(getString(R.string.ssrsub_error, e.getMessage))
              }.foreach(result => {
                handler.post(() => {
                  result.foreach(msg => Toast.makeText(configActivity, msg, Toast.LENGTH_SHORT).show())
                  testProgressDialog.dismiss
                })
              })
            }
          }
        }
      }): DialogInterface.OnClickListener)
      .setNegativeButton(android.R.string.no, null)
      .setView(view)
      .create()
      .show()
  }

  private def notifyGroupNameChange (groupName: Option[String]): Unit = {
    groupName match {
      case Some(name) => configActivity.putStringExtra(Key.SUBSCRIPTION_GROUP_NAME, name)
      case None => configActivity.putStringExtra(Key.SUBSCRIPTION_GROUP_NAME, getString(R.string.allgroups))
    }
  }

  // TODO: Future.sequence
  private[this] def updateAllSubscriptions (): Unit = {
    Utils.ThrowableFuture {
      handler.post(() => {
        testProgressDialog = ProgressDialog.show(requireContext(), getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)
      })
      app.ssrsubManager.getAllSSRSubs match {
        case Some(ssrsubs) => ssrsubs.foreach(updateSingleSubscription)
        case _ => configActivity.runOnUiThread(() => {
          Toast.makeText(requireContext(), R.string.action_export_err, Toast.LENGTH_SHORT).show
        })
      }
      handler.post(() => {
        testProgressDialog.dismiss
        testProgressDialog = null
      })
//      finish()
//      startActivity(new Intent(getIntent()))
    }
  }

  private def updateSingleSubscription (ssrsub: SSRSub): Unit = {
    SSRSub.getSubscriptionResponse(ssrsub.url)
      .flatMap(response => Try {
        ssrsub.addProfiles(response)
        notifyGroupNameChange(Some(ssrsub.url_group))
        None
      }).recover{
      case e: Exception => Some(getString(R.string.ssrsub_error, e.getMessage))
    }.foreach(result => result.foreach(msg =>
      configActivity.runOnUiThread(() => Toast.makeText(getActivity, msg, Toast.LENGTH_SHORT).show)
    ))
  }

  private[this] def setupRemoveSubscription (ssusubsList: RecyclerView): Unit = {
    new ItemTouchHelper(new SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
      ItemTouchHelper.START | ItemTouchHelper.END) {
      def onSwiped(viewHolder: ViewHolder, direction: Int) = {
        val index = viewHolder.getAdapterPosition
        new AlertDialog.Builder(getActivity)
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
            val delete_profiles = app.profileManager.getAllProfilesBySSRSub(ssrsubItem) match {
              case Some(profiles) =>
                profiles.filter(profile=> profile.ssrsub_id <= 0 || profile.ssrsub_id == ssrsubItem.id)
              case _ => List()
            }

            delete_profiles.foreach((profile: Profile) => {
              if (profile.id != app.profileId) {
                app.profileManager.delProfile(profile.id)
              }
            })

            val index = viewHolder.getAdapterPosition
            val item = viewHolder.asInstanceOf[SSRSubViewHolder].item
            ssrsubAdapter.remove(index)
            app.ssrsubManager.delSSRSub(item.id)
            notifyGroupNameChange(None)
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
  }

  private final class SSRSubViewHolder(val view: View) extends RecyclerView.ViewHolder(view)
    with View.OnClickListener with View.OnKeyListener with PopupMenu.OnMenuItemClickListener {

    var item: SSRSub = _
    private val text1 = itemView.findViewById(android.R.id.text1).asInstanceOf[TextView]
    private val text2 = itemView.findViewById(android.R.id.text2).asInstanceOf[TextView]
    private val ivUpdateSubscription = itemView.findViewById(R.id.update_subscription).asInstanceOf[ImageView]
    private val ivEditSubscription = itemView.findViewById(R.id.edit_subscription).asInstanceOf[ImageView]
    text1.setOnClickListener(this)
    ivUpdateSubscription.setOnClickListener(_ => {
      Utils.ThrowableFuture {
        handler.post(() => {
          testProgressDialog = ProgressDialog.show(getActivity, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)
        })
        updateSingleSubscription(item)
        handler.post(() => {
          testProgressDialog.dismiss
          testProgressDialog = null
        })
      }
    })
    ivEditSubscription.setOnClickListener(_ => {
      val popup = new PopupMenu(requireContext(), ivEditSubscription)
      popup.getMenuInflater.inflate(R.menu.subscription_edit_popup, popup.getMenu)
      popup.setOnMenuItemClickListener(this)
      popup.show()

    })


    def updateText(isShowUrl: Boolean = false) {
      val builder = new SpannableStringBuilder
//      builder.append(this.item.url_group)
      if (isShowUrl) {
        val start = builder.length
        builder.append(this.item.url)
        builder.setSpan(new TextAppearanceSpan(getActivity, android.R.style.TextAppearance_Small),
          start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
      handler.post(() => {
        text1.setText(this.item.url_group)
        if (!TextUtils.isEmpty(builder)) {
          text2.setText(builder)
          text2.setVisibility(View.VISIBLE)
        } else {
          text2.setVisibility(View.GONE)
        }
      })
    }

    def bind(item: SSRSub) {
      this.item = item
      updateText()
    }

    def onClick(v: View) = {
      updateText(true)
    }

    override def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
      case R.id.action_edit_subscription => {
        edit_subscription()
        true
      }
      case R.id.action_copy_subscription => {
        clipboard.setPrimaryClip(ClipData.newPlainText(null, this.item.url))
        true
      }
      case _ => false
    }

    def edit_subscription(): Unit = {
      showSubscriptionDialog(Some(item)) { (responseString, url, groupName) => {
        (url, groupName) match {
          case t if t._1 == item.url && t._2 != item.url_group => {
            Utils.ThrowableFuture {
              this.item.url_group = groupName
              app.ssrsubManager.updateSSRSub(item)
              app.profileManager.updateGroupName(groupName, item.id)
              updateText(false)
              notifyGroupNameChange(Some(groupName))
            }
          }
          case t if t._1 != item.url => {
            item.url = url
            item.url_group = groupName
            app.ssrsubManager.updateSSRSub(item)
            item.addProfiles(responseString)
//            addProfilesFromSubscription(item, responseString)
            updateText(false)
            notifyGroupNameChange(Some(groupName))
          }
          case _ =>
        }
      }
      }
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
      new SSRSubViewHolder(LayoutInflater.from(vg.getContext).inflate(R.layout.layout_ssr_sub_item1, vg, false))

    def add(item: SSRSub) {
      val pos = getItemCount
      profiles += item
      notifyItemInserted(pos)
    }

    def remove(pos: Int) {
      profiles.remove(pos)
      notifyItemRemoved(pos)
    }
  }
}
