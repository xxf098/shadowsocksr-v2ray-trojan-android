package com.github.shadowsocks.fragments

import java.nio.charset.StandardCharsets

import android.app.TaskStackBuilder
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.util.Log
import android.view.{LayoutInflater, MenuItem, View, ViewGroup}
import android.widget.{EditText, TextView, Toast}
import com.github.shadowsocks.ConfigActivity
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.utils.{ConfigUtils, Key, Parser}
import com.google.gson.{GsonBuilder, JsonParser}
import com.github.shadowsocks.R

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import tun2socks.Tun2socks

class V2RayConfigFragment extends Fragment with OnMenuItemClickListener {

  private final val TAG = "V2RayConfigFragment"
  private var etConfig: EditText = _
  private var profile: Profile = _


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.layout_v2ray_config, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    val activity = getActivity.asInstanceOf[ConfigActivity]
    activity.toolbar.inflateMenu(R.menu.v2ray_config_menu)
    activity.toolbar.setOnMenuItemClickListener(this)
    etConfig = view.findViewById(R.id.config_view).asInstanceOf[EditText]
    val profileId = getArguments.getInt(Key.EXTRA_PROFILE_ID, -1)
    profileId match {
      case -1 => etConfig.setText(ConfigUtils.V2RAY_CONFIG, TextView.BufferType.EDITABLE)
      case _ => {
        app.profileManager.getProfile(profileId) match {
          case Some(p) => {
            profile = p
            etConfig.setText(profile.v_json_config, TextView.BufferType.EDITABLE)
          }
          case None => getActivity.finish()
        }
      }
    }
  }

  def runOnUiThread(action: Runnable) = getActivity.runOnUiThread(action)

  def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
    case R.id.action_save_v2ray_config => {
      saveConfig(etConfig.getText.toString)
      true
    }
    case R.id.action_clear_v2ray_config => {
      etConfig.setText("", TextView.BufferType.EDITABLE)
      true
    }
    case _ => false
  }

  def saveConfig(config: String): Unit = {
    val future = checkConfig(config)
    future onSuccess {
      case Some(newProfile) => {
        runOnUiThread(() => {
          if (profile == null) {
            profile = app.profileManager.createProfile(newProfile)
          } else {
            newProfile.id = profile.id
            newProfile.url_group = profile.url_group
            newProfile.name = profile.name
            app.profileManager.updateProfile(newProfile)
          }
          Toast.makeText(getActivity, "Saved", Toast.LENGTH_SHORT).show()
        })
      }
      case _ => runOnUiThread(() => Toast.makeText(getActivity, "Config is not valid!", Toast.LENGTH_SHORT))
    }
    future onFailure {
      case e: Exception => {
        e.printStackTrace()
        runOnUiThread(() => Toast.makeText(getActivity, s"config is not valid: ${e.getMessage}", Toast.LENGTH_SHORT).show())
      }
    }
  }


  // display id aid
  def checkConfig(config: String): Future[Option[Profile]] = {
    Future {
      val jsonObject = new JsonParser().parse(config).getAsJsonObject
      val prettyConfig = new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject)
      for {
        p <- Option(prettyConfig).map(Parser.getV2RayJSONProfile)
        v <- Try(Tun2socks.convertJSONToVmess(config.getBytes(StandardCharsets.UTF_8))).toOption
      } yield {
        // Log.e("V2RayConfigFragment", s"${v.getAdd}, ${v.getPort} ${v.getID} ${v.getAid} ${v.getSecurity}")
        p.v_add = v.getAdd
        p.v_port = s"${v.getPort}"
        p.v_id = v.getID
        p.v_aid = s"${v.getAid}"
        p.v_security = v.getSecurity
        p
      }
    }
  }
}
