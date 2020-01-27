package com.github.shadowsocks.database

import java.io.IOException
import java.nio.charset.StandardCharsets

import android.text.TextUtils
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.Utils
import tun2socks.Tun2socks

trait ProfileAction {
  def getElapsed(profile: Profile) : Long
}

object VmessAction extends ProfileAction {
  override def getElapsed(profile: Profile): Long = {
    if (!Utils.isNumeric(profile.v_add)) Utils.resolve(profile.v_add, enableIPv6 = true, hostname = "1.1.1.1") match {
      case Some(addr) => profile.v_add = addr
      case None => throw new IOException("Name Not Resolved")
    }
    Tun2socks.testVmessLatency(profile, app.getV2rayAssetsPath())
  }
}

object V2JSONAction extends ProfileAction {
  override def getElapsed(profile: Profile): Long = {
    if (TextUtils.isEmpty(profile.v_add)) throw new IOException("Server Address Not Found!")
    if (!Utils.isNumeric(profile.v_add)) Utils.resolve(profile.v_add, enableIPv6 = true, hostname = "1.1.1.1") match {
      case Some(addr) => profile.v_add = addr
      case None => throw new IOException("Name Not Resolved")
    }
    val config = "\"address\":\\s*\".+?\"".r.replaceFirstIn(profile.v_json_config, s""""address": "${profile.v_add}"""")
    Tun2socks.testConfigLatency(config.getBytes(StandardCharsets.UTF_8), app.getV2rayAssetsPath())
  }
}

object ProfileMixin {
  implicit class ProfileExt(profile1: Profile) extends ProfileAction {
    var profileAction: ProfileAction = profile1 match {
      case p if p.isVmess => VmessAction
      case p if p.isV2RayJSON => V2JSONAction
      case _ => throw new Exception("Not Supported!")
    }
    override def getElapsed(profile: Profile = profile1): Long = profileAction.getElapsed(profile)
  }
}