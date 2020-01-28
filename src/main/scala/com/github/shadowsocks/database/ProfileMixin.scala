package com.github.shadowsocks.database

import java.io.IOException
import java.nio.charset.StandardCharsets

import android.text.TextUtils
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.Utils
import tun2socks.Tun2socks


trait ProfileAction {
  var profile: Profile = _
  def getElapsed() : Long
  def isOK(): Boolean
}

object SSRAction extends ProfileAction {
  override def getElapsed(): Long = throw new Exception("Not Supported!")

  override def isOK(): Boolean = !(TextUtils.isEmpty(profile.host) || TextUtils.isEmpty(profile.password))
}

object VmessAction extends ProfileAction {
  override def getElapsed(): Long = {
    if (!Utils.isNumeric(profile.v_add)) Utils.resolve(profile.v_add, enableIPv6 = true, hostname = "1.1.1.1") match {
      case Some(addr) => profile.v_add = addr
      case None => throw new IOException("Name Not Resolved")
    }
    Tun2socks.testVmessLatency(profile, app.getV2rayAssetsPath())
  }

  override def isOK(): Boolean = !(TextUtils.isEmpty(profile.v_add) ||
    TextUtils.isEmpty(profile.v_port) ||
    TextUtils.isEmpty(profile.v_id) ||
    TextUtils.isEmpty(profile.v_aid) ||
    TextUtils.isEmpty(profile.v_net))
}

object V2JSONAction extends ProfileAction {
  override def getElapsed(): Long = {
    if (TextUtils.isEmpty(profile.v_add)) throw new IOException("Server Address Not Found!")
    if (!Utils.isNumeric(profile.v_add)) Utils.resolve(profile.v_add, enableIPv6 = true, hostname = "1.1.1.1") match {
      case Some(addr) => profile.v_add = addr
      case None => throw new IOException("Name Not Resolved")
    }
    val config = "\"address\":\\s*\".+?\"".r.replaceFirstIn(profile.v_json_config, s""""address": "${profile.v_add}"""")
    Tun2socks.testConfigLatency(config.getBytes(StandardCharsets.UTF_8), app.getV2rayAssetsPath())
  }

  override def isOK(): Boolean = !TextUtils.isEmpty(profile.v_json_config)
}

object ProfileMixin {
  implicit class ProfileExt(profile: Profile) extends ProfileAction {
    val profileAction: ProfileAction = profile match {
      case p if p.isVmess => VmessAction
      case p if p.isV2RayJSON => V2JSONAction
      case p if !p.isV2Ray => SSRAction
      case _ => throw new Exception("Not Supported!")
    }
    profileAction.profile = profile
    override def getElapsed(): Long = profileAction.getElapsed()

    override def isOK(): Boolean = profileAction.isOK()
  }
}