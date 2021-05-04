package com.github.shadowsocks

import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.os.{Build, Bundle}
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.{AppCompatSpinner, DefaultItemAnimator, LinearLayoutManager, RecyclerView, Toolbar}
import android.util.Log
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.{AdapterView, ArrayAdapter, CheckedTextView, TextView}
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.utils.{Key, Utils}
import com.github.shadowsocks.ShadowsocksApplication.app

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Lucas on 3/10/16.
  */
class ShadowsocksQuickSwitchActivity extends AppCompatActivity {

  private var currentGroupName: String = _
  private lazy val profilesAdapter: ProfilesAdapter = new ProfilesAdapter()

  private class ProfileViewHolder(val view: View) extends RecyclerView.ViewHolder(view) with View.OnClickListener {
    {
      val typedArray = obtainStyledAttributes(Array(android.R.attr.selectableItemBackground))
      view.setBackgroundResource(typedArray.getResourceId(0, 0))
      typedArray.recycle
    }
    private var item: Profile = _
    private val text = itemView.findViewById(android.R.id.text1).asInstanceOf[CheckedTextView]
    itemView.setOnClickListener(this)

    def bind(item: Profile) {
      this.item = item
      text.setText(item.name)
      text.setChecked(item.id == app.profileId)
    }

    def onClick(v: View) {
      app.switchProfile(item.id)
      Utils.startSsService(ShadowsocksQuickSwitchActivity.this)
      finish
    }
  }

  private class ProfilesAdapter extends RecyclerView.Adapter[ProfileViewHolder] {
    var profiles = ProfileManagerActivity.getProfilesByGroup(currentGroupName, false, false)

    def getItemCount = profiles.length

    def onBindViewHolder(vh: ProfileViewHolder, i: Int) = i match {
      case _ => vh.bind(profiles(i))
    }

    def onGroupChange():Unit = {
      profiles = ProfileManagerActivity.getProfilesByGroup(currentGroupName, false, false)
      notifyDataSetChanged()
    }

    private val name = "select_dialog_singlechoice_" + (if (Build.VERSION.SDK_INT >= 21) "material" else "holo")

    def onCreateViewHolder(vg: ViewGroup, i: Int) = new ProfileViewHolder(LayoutInflater.from(vg.getContext)
      .inflate(Resources.getSystem.getIdentifier(name, "layout", "android"), vg, false))
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_quick_switch)

    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.quick_switch)
    initSpinner(Some(app.settings.getString(Key.currentGroupName, getString(R.string.allgroups))))

    val profilesList = findViewById(R.id.profilesList).asInstanceOf[RecyclerView]
    val lm = new LinearLayoutManager(this)
    profilesList.setLayoutManager(lm)
    profilesList.setItemAnimator(new DefaultItemAnimator)
    profilesList.setAdapter(profilesAdapter)
    if (app.profileId >= 0) lm.scrollToPosition(profilesAdapter.profiles.zipWithIndex.collectFirst {
      case (profile, i) if profile.id == app.profileId => i
    }.getOrElse(0))
    if (Build.VERSION.SDK_INT >= 25) getSystemService(classOf[ShortcutManager]).reportShortcutUsed("switch")
  }

  def initSpinner (groupName: Option[String] = None, ignoreGroupName: Option[String] = None ): Unit = {
    currentGroupName = groupName.getOrElse(getString(R.string.allgroups))
    val spinner = findViewById(R.id.group_switch_spinner).asInstanceOf[AppCompatSpinner]
    val spinnerAdapter = new ArrayAdapter[String](this, android.R.layout.simple_spinner_item)
    val selectIndex = app.profileManager.getGroupNames match {
      case Some(groupNames) => {
        val allGroupNames = getString(R.string.allgroups) +: groupNames
        allGroupNames.filter(_ != ignoreGroupName.orNull).foreach(name => spinnerAdapter.add(name))
        Math.max(0, allGroupNames.indexOf(currentGroupName))
      }
      case None => 0
    }
    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.setAdapter(spinnerAdapter)
    spinner.setSelection(selectIndex)
    spinner.setOnItemSelectedListener(new OnItemSelectedListener{
      override def onItemSelected(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
        currentGroupName = parent.getItemAtPosition(position).toString
        app.editor.putString(Key.currentGroupName, currentGroupName).apply()
        profilesAdapter.onGroupChange()
      }
      override def onNothingSelected(parent: AdapterView[_]): Unit = {}
    })
  }
}
