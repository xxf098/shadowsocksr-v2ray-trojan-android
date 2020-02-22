package com.github.shadowsocks.fragments

import java.io.IOException
import java.util.concurrent.TimeUnit

import android.app.ProgressDialog
import android.content.{DialogInterface, Intent}
import android.os.{Bundle, Handler}
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback
import android.support.v7.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import android.text.style.TextAppearanceSpan
import android.text.{SpannableStringBuilder, Spanned, TextUtils}
import android.util.Log
import android.view.{KeyEvent, LayoutInflater, MenuItem, View, ViewGroup}
import android.widget.{EditText, ImageView, TextView, Toast}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.{Profile, SSRSub}
import com.github.shadowsocks.utils.{Key, Parser, Utils}
import com.github.shadowsocks.widget.UndoSnackbarManager
import com.github.shadowsocks.{ConfigActivity, ProfileManagerActivity, R}
import okhttp3.{OkHttpClient, Request}

import scala.collection.mutable.ArrayBuffer

class SubscriptionFragment extends Fragment with OnMenuItemClickListener {

  private final val TAG = "SubscriptionFragment"
  private val handler = new Handler
  private lazy val ssrsubAdapter = new SSRSubAdapter
  private var testProgressDialog: ProgressDialog = _
  private lazy val configActivity = getActivity.asInstanceOf[ConfigActivity]

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

    val ssusubsList = view.findViewById(R.id.ssrsubList).asInstanceOf[RecyclerView]
    val layoutManager = new LinearLayoutManager(activity)
    ssusubsList.setLayoutManager(layoutManager)
    ssusubsList.setItemAnimator(new DefaultItemAnimator)
    ssusubsList.setAdapter(ssrsubAdapter)
    setupRemoveSubscription(ssusubsList)
  }

  def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
    case R.id.action_add_subscription => {
      addSubscription(None)
      true
    }
    case R.id.action_update_subscription => {
      updateAllSubscriptions()
      true
    }
    case _ => false
  }

  private[this] def addSubscription(url: Option[String]): Unit = {
    val context = getActivity
    val UrlAddEdit = new EditText(context)
    new AlertDialog.Builder(context)
      .setTitle(getString(R.string.ssrsub_add))
      .setPositiveButton(android.R.string.ok, ((_, _) => {
        if(!TextUtils.isEmpty(UrlAddEdit.getText().toString())) {
          Utils.ThrowableFuture {
            handler.post(() => testProgressDialog = ProgressDialog.show(context, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true))
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
          }
        }
      }): DialogInterface.OnClickListener)
      .setNegativeButton(android.R.string.no, null)
      .setView(UrlAddEdit)
      .create()
      .show()
  }

  // TODO: Future.sequence
  private[this] def updateAllSubscriptions (): Unit = {
    Utils.ThrowableFuture {
      handler.post(() => {
        testProgressDialog = ProgressDialog.show(getActivity, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)
      })
      app.ssrsubManager.getAllSSRSubs match {
        case Some(ssrsubs) => ssrsubs.foreach(updateSingleSubscription)
        case _ => Toast.makeText(getActivity, R.string.action_export_err, Toast.LENGTH_SHORT).show
      }
      handler.post(() => {
        testProgressDialog.dismiss
      })
//      finish()
//      startActivity(new Intent(getIntent()))
    }
  }

  private def updateSingleSubscription (ssrsub: SSRSub): Unit = {
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
            configActivity.putStringExtra(Key.SUBSCRIPTION_GROUP_NAME, profile.url_group)
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
            val delete_profiles = app.profileManager.getAllProfilesByGroup(ssrsubItem.url_group) match {
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
            configActivity.putStringExtra(Key.SUBSCRIPTION_GROUP_NAME, getString(R.string.allgroups))
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
    with View.OnClickListener with View.OnKeyListener {

    var item: SSRSub = _
    private val text1 = itemView.findViewById(android.R.id.text1).asInstanceOf[TextView]
    private val text2 = itemView.findViewById(android.R.id.text2).asInstanceOf[TextView]
    private val ivUpdateSubscription = itemView.findViewById(R.id.update_subscription).asInstanceOf[ImageView]
    text1.setOnClickListener(this)
    ivUpdateSubscription.setOnClickListener(_ => {
      Utils.ThrowableFuture {
        handler.post(() => {
          testProgressDialog = ProgressDialog.show(getActivity, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)
        })
        updateSingleSubscription(item)
        handler.post(() => {
          testProgressDialog.dismiss
        })
      }
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
