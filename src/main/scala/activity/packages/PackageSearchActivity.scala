package org.fedoraproject.mobile

import Pkgwat._
import Implicits._

import android.app.SearchManager
import android.content.{ Context, Intent }
import android.os.Bundle
import android.util.Log
import android.view.{ LayoutInflater, Menu, View, ViewGroup }
import android.widget.AdapterView.OnItemClickListener
import android.widget.{ AdapterView, ArrayAdapter, ImageView, LinearLayout, ListView, TextView, Toast, SearchView }

import scalaz._, Scalaz._

class PackageSearchActivity extends TypedActivity with util.Views {
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.package_search_activity)
    handleIntent(getIntent())
  }

  override def onNewIntent(intent: Intent): Unit = {
    setIntent(intent)
    handleIntent(intent)
  }

  def handleIntent(intent: Intent): Unit = {
    if (intent.getAction == Intent.ACTION_SEARCH) {
      findViewOpt(TR.packages).map { v =>
        v.setAdapter(null)
        v.setVisibility(View.GONE)
      }

      findViewOpt(TR.progress).map(_.setVisibility(View.VISIBLE))

      val queryText = intent.getStringExtra(SearchManager.QUERY)
      val queryObject = FilteredQuery(
        500, // TODO: Pagination...and, well, unhacking this.
        0,
        Map("search" -> queryText))

      Pkgwat.queryJson(queryObject).runAsync(_.fold(
        _ => Toast.makeText(this, R.string.packages_search_failure, Toast.LENGTH_LONG).show,
        right => right.fold(
          _ => Toast.makeText(this, R.string.packages_search_failure, Toast.LENGTH_LONG).show,
          res => {
            val packages = res.rows.toArray

            class PackageAdapter(
              context: Context,
              resource: Int,
              items: Array[FedoraPackage])
              extends ArrayAdapter[FedoraPackage](context, resource, items) {
              override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
                val pkg = getItem(position)

                val layout = LayoutInflater.from(context)
                  .inflate(R.layout.package_list_item, parent, false)
                  .asInstanceOf[LinearLayout]

                val iconView = layout
                  .findViewById(R.id.icon)
                  .asInstanceOf[ImageView]

                BitmapFetch.fromPackage(pkg).runAsync(_.fold(
                  err => {
                    Log.e("PackageSearchActivity", err.toString)
                    runOnUiThread(iconView.setImageResource(R.drawable.ic_search))
                  },
                  icon => runOnUiThread(iconView.setImageBitmap(icon))
                ))

                layout
                  .findViewById(R.id.title)
                  .asInstanceOf[TextView]
                  .setText(pkg.name)

                layout
              }
            }

            val adapter = new PackageAdapter(
              this,
              android.R.layout.simple_list_item_1,
              packages)

            runOnUiThread {
              findViewOpt(TR.progress).map(_.setVisibility(View.GONE))
            }

            val packagesView = findViewOpt(TR.packages).map(_.asInstanceOf[ListView])
            packagesView.cata(
              v => {
                runOnUiThread(v.post(v.setAdapter(adapter)))
                runOnUiThread(v.setVisibility(View.VISIBLE))
                runOnUiThread(v.setOnItemClickListener(new OnItemClickListener {
                  def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
                    val pkg = packages(position)
                    val intent = new Intent(PackageSearchActivity.this, classOf[PackageInfoActivity])
                    intent.putExtra("package", pkg)
                    startActivity(intent)
                  }
                }))
              },
              ()
            )
          }
        )
      ))
    }
    ()
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater = getMenuInflater
    inflater.inflate(R.menu.search, menu)
    val searchManager = getSystemService(Context.SEARCH_SERVICE).asInstanceOf[SearchManager]
    val searchViewMenuItem = menu.findItem(R.id.menu_search)
    val searchView = searchViewMenuItem.getActionView.asInstanceOf[SearchView]
    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName))
    searchView.setIconifiedByDefault(false)
    searchViewMenuItem.expandActionView
    true
  }
}
