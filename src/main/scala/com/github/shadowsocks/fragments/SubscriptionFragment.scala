package com.github.shadowsocks.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.{LayoutInflater, View, ViewGroup}
import com.github.shadowsocks.{ConfigActivity, R}

class SubscriptionFragment extends Fragment  {

  private final val TAG = "SubscriptionFragment"

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.layout_subscriptions, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    val activity = getActivity.asInstanceOf[ConfigActivity]
    activity.toolbar.setTitle(R.string.subscriptions)
  }
}
