package info.papdt.express.helper.ui.fragment.home

import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.LinearLayout

import com.h6ah4i.android.widget.advrecyclerview.swipeable.RecyclerViewSwipeManager
import com.h6ah4i.android.widget.advrecyclerview.touchguard.RecyclerViewTouchActionGuardManager
import com.scwang.smartrefresh.layout.SmartRefreshLayout

import com.scwang.smartrefresh.layout.api.RefreshLayout
import com.scwang.smartrefresh.layout.listener.OnRefreshListener
import info.papdt.express.helper.R
import info.papdt.express.helper.dao.PackageDatabase
import info.papdt.express.helper.ui.MainActivity
import info.papdt.express.helper.ui.adapter.HomePackageListAdapter
import info.papdt.express.helper.ui.common.AbsFragment
import info.papdt.express.helper.view.AnimatedRecyclerView
import info.papdt.express.helper.view.DeliveryHeader
import moe.feng.kotlinyan.common.findNonNullView

abstract class BaseFragment : AbsFragment, OnRefreshListener {

	private val mRefreshLayout: SmartRefreshLayout by findNonNullView(R.id.refresh_layout)
	private val mRecyclerView: AnimatedRecyclerView by findNonNullView(R.id.recycler_view)
	private val mEmptyView: LinearLayout by findNonNullView(R.id.empty_view)

	private var mAdapter: RecyclerView.Adapter<*>? = null
	private var mSwipeManager: RecyclerViewSwipeManager? = null

	protected var database: PackageDatabase? = null
		private set
	private var hasPlayedAnimation = false

	internal var eggCount = 0
	internal var bigEggCount = 0

	constructor(database: PackageDatabase) {
		this.database = database
	}

	constructor() : super()

	// official method to get Activity Context
	override fun onAttach(activity: Activity) {
		super.onAttach(activity)
		sInstance = activity
	}

	// restore database to reconstruct express info
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		this.database = PackageDatabase.getInstance(sInstance!!)
	}

	override fun getLayoutResId(): Int {
		return R.layout.fragment_home
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		// Set up mRecyclerView
		mSwipeManager = RecyclerViewSwipeManager()
		mRecyclerView.setHasFixedSize(false)
		mRecyclerView.setLayoutManager(
				LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		)

		// Set up mRefreshLayout
		mRefreshLayout.setOnRefreshListener(this)
		mRefreshLayout.setRefreshHeader(DeliveryHeader(view.context))

		setUpAdapter()
		mEmptyView.visibility = if (mAdapter != null && mAdapter!!.itemCount > 0) View.GONE else View.VISIBLE
		mEmptyView.setOnClickListener(View.OnClickListener {
			if (eggCount++ > 7) {
				eggCount = 0
				bigEggCount++
				val eggView = `$`<View>(R.id.sun) ?: return@OnClickListener
				eggView.rotation = 0f
				eggView.animate().rotation(360f * bigEggCount).setDuration(1000).start()
				if (bigEggCount > 3) bigEggCount = 0
			}
		})
	}

	protected abstract fun setUpAdapter()
	abstract val fragmentId: Int

	override fun onRefresh(view: RefreshLayout) {
		mHandler.sendEmptyMessage(FLAG_REFRESH_LIST)
	}

	fun notifyDataSetChanged() {
		mHandler.sendEmptyMessage(FLAG_UPDATE_ADAPTER_ONLY)
	}

	fun scrollToTop() {
		if (mAdapter != null && mAdapter!!.itemCount > 0) {
			mRecyclerView.smoothScrollToPosition(0)
		}
	}

	protected fun playListAnimation() {
		if (!hasPlayedAnimation) {
			hasPlayedAnimation = true
			mRecyclerView.scheduleLayoutAnimation()
		}
	}

	protected fun setAdapter(adapter: RecyclerView.Adapter<*>) {
		this.mAdapter = adapter
		mRecyclerView.adapter = mSwipeManager!!.createWrappedAdapter(mAdapter!!)
		mSwipeManager!!.attachRecyclerView(mRecyclerView)
		RecyclerViewTouchActionGuardManager().attachRecyclerView(mRecyclerView)
		mEmptyView.visibility = if (mAdapter != null && mAdapter!!.itemCount > 0) View.GONE else View.VISIBLE

		/** Set undo operation  */
		(adapter as? HomePackageListAdapter)?.setOnDataRemovedCallback { _, title ->
			val msg = Message()
			msg.what = MainActivity.MSG_NOTIFY_ITEM_REMOVE
			msg.arg1 = fragmentId
			val data = Bundle()
			data.putString("title", title)
			msg.data = data

			mainActivity.mHandler.sendMessage(msg)

			mainActivity.notifyDataChanged(fragmentId)
		}
	}

	protected val mainActivity: MainActivity
		get() = activity as MainActivity

	fun onUndoActionClicked() {
		val position = database!!.undoLastRemoval()
		if (position >= 0 && mAdapter != null) {
			mAdapter!!.notifyDataSetChanged()
			mainActivity.notifyDataChanged(fragmentId)
		}
	}

	private val mHandler = object : Handler() {
		override fun handleMessage(msg: Message) {
			when (msg.what) {
				FLAG_REFRESH_LIST -> {
					if (!mRefreshLayout.isRefreshing) {
						mRefreshLayout.autoRefresh()
					}
					RefreshTask().execute()
				}
				FLAG_UPDATE_ADAPTER_ONLY -> if (mAdapter != null) {
					mAdapter!!.notifyDataSetChanged()
					playListAnimation()
					mEmptyView.visibility = if (mAdapter != null && mAdapter!!.itemCount > 0) View.GONE else View.VISIBLE
				}
			}
		}
	}

	inner class RefreshTask : AsyncTask<Void, Void, Void>() {

		override fun doInBackground(vararg voids: Void): Void? {
			database!!.pullDataFromNetwork(false)
			return null
		}

		override fun onPostExecute(msg: Void) {
			hasPlayedAnimation = false
			mRefreshLayout.finishRefresh()
			mHandler.sendEmptyMessage(FLAG_UPDATE_ADAPTER_ONLY)
		}

	}

	companion object {

		private var sInstance: Context? = null
		private const val FLAG_REFRESH_LIST = 0
		private const val FLAG_UPDATE_ADAPTER_ONLY = 1

	}

}