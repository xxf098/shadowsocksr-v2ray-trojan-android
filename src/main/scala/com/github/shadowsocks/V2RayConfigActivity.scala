package com.github.shadowsocks

import android.app.TaskStackBuilder
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.util.Log
import android.view.{MenuItem, WindowManager}
import android.widget.{EditText, TextView}
import com.github.shadowsocks.utils.ConfigUtils


class V2RayConfigActivity extends AppCompatActivity with
  OnMenuItemClickListener{

  private final val TAG = "V2RayConfigActivity"

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    getWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.layout_v2ray_config)

    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.v2ray_config)
    toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
    toolbar.setNavigationOnClickListener(_ => {
      val intent = getParentActivityIntent
      if (shouldUpRecreateTask(intent) || isTaskRoot)
        TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).startActivities()
      else finish()
    })
    toolbar.inflateMenu(R.menu.v2ray_config_menu)
    toolbar.setOnMenuItemClickListener(this)
    val configView = findViewById(R.id.config_view).asInstanceOf[EditText]
    configView.setText(ConfigUtils.V2RAY_CONFIG, TextView.BufferType.EDITABLE)
  }

  def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
    case R.id.action_save_v2ray_config => {
      Log.e(TAG, "action_save_v2ray_config")
      true
    }
    case _ => false
  }
}
