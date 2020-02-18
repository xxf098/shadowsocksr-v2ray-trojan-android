package com.github.shadowsocks.fragments

import android.os.{Bundle, Handler}
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.support.v7.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import android.text.style.TextAppearanceSpan
import android.text.{SpannableStringBuilder, Spanned}
import android.util.Log
import android.view.{KeyEvent, LayoutInflater, MenuItem, View, ViewGroup}
import android.widget.{TextView, Toast}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.{Profile, SSRSub}
import com.github.shadowsocks.widget.UndoSnackbarManager
import com.github.shadowsocks.{ConfigActivity, ProfileManagerActivity, R}

import scala.collection.mutable.ArrayBuffer

class SubscriptionFragment extends Fragment with OnMenuItemClickListener  {

  private final val TAG = "SubscriptionFragment"
  private val handler = new Handler
  private lazy val ssrsubAdapter = new SSRSubAdapter

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

  }

  def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
    case R.id.action_add_subscription => {
      true
    }
    case R.id.action_update_subscription => {
      true
    }
    case _ => false
  }

  private final class SSRSubViewHolder(val view: View) extends RecyclerView.ViewHolder(view)
    with View.OnClickListener with View.OnKeyListener {

    var item: SSRSub = _
    private val text = itemView.findViewById(android.R.id.text2).asInstanceOf[TextView]
    itemView.setOnClickListener(this)

    def updateText(isShowUrl: Boolean = false) {
      val builder = new SpannableStringBuilder
      builder.append(this.item.url_group)
      if (isShowUrl) {
        val start = builder.length
        builder.append("\n" + this.item.url)
        builder.setSpan(new TextAppearanceSpan(getActivity, android.R.style.TextAppearance_Small),
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
