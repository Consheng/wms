package ykk.xc.com.wms.warehouse

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.text.Html
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import butterknife.OnClick
import kotlinx.android.synthetic.main.ware_inventorynow_by_stock_dialog.*
import okhttp3.*
import ykk.xc.com.wms.R
import ykk.xc.com.wms.bean.*
import ykk.xc.com.wms.comm.BaseDialogActivity
import ykk.xc.com.wms.util.JsonUtil
import ykk.xc.com.wms.util.basehelper.BaseRecyclerAdapter
import ykk.xc.com.wms.util.xrecyclerview.XRecyclerView
import ykk.xc.com.wms.warehouse.adapter.InventoryNowByStockId_DialogAdapter
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*

/**
 * 选择盘点方案dialog
 */
class InventoryNowByStock_DialogActivity : BaseDialogActivity(), XRecyclerView.LoadingListener {

    internal var btnSearch: Button? = null
    private val context = this
    private val listDatas = ArrayList<InventoryNow>()
    private var mAdapter: InventoryNowByStockId_DialogAdapter? = null
    private val okHttpClient = OkHttpClient()
    private var limit = 1
    private var isRefresh: Boolean = false
    private var isLoadMore: Boolean = false
    private var isNextPage: Boolean = false
    private var icItemId = 0 // 物料id
    private var stockId = 0
    private var stockAreaId = 0
    private var storageRackId = 0
    private var stockPositionId = 0
    private var batchCode:String? = null

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: InventoryNowByStock_DialogActivity) : Handler() {
        private val mActivity: WeakReference<InventoryNowByStock_DialogActivity>

        init {
            mActivity = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            val m = mActivity.get()
            if (m != null) {
                m.hideLoadDialog()
                when (msg.what) {
                    SUCC1 // 成功
                    -> {
                        val list = JsonUtil.strToList2(msg.obj as String, InventoryNow::class.java)
                        m.listDatas.addAll(list!!)
                        m.mAdapter!!.notifyDataSetChanged()

                        if (m.isRefresh) {
                            m.xRecyclerView!!.refreshComplete(true)
                        } else if (m.isLoadMore) {
                            m.xRecyclerView!!.loadMoreComplete(true)
                        }

                        m.xRecyclerView!!.isLoadingMoreEnabled = m.isNextPage
                    }
                    UNSUCC1 // 数据加载失败！
                    -> {
                        m.mAdapter!!.notifyDataSetChanged()
                        m.toasts("抱歉，没有加载到数据！")
                    }
                }
            }
        }

    }

    override fun setLayoutResID(): Int {
        return R.layout.ware_inventorynow_by_stock_dialog
    }

    override fun initView() {
        xRecyclerView!!.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        xRecyclerView!!.layoutManager = LinearLayoutManager(context)
        mAdapter = InventoryNowByStockId_DialogAdapter(context, listDatas)
        xRecyclerView!!.adapter = mAdapter
        xRecyclerView!!.setLoadingListener(context)

        xRecyclerView!!.isPullRefreshEnabled = false // 上啦刷新禁用
        //        xRecyclerView.setLoadingMoreEnabled(false); // 不显示下拉刷新的view

        mAdapter!!.onItemClickListener = BaseRecyclerAdapter.OnItemClickListener { adapter, holder, view, pos ->
            val m = listDatas[pos - 1]
            val intent = Intent()
            intent.putExtra("obj", m)
            context.setResult(Activity.RESULT_OK, intent)
            context.finish()
        }
    }

    override fun initData() {
        val bundle = context.intent.extras
        if (bundle != null) {
            icItemId = bundle.getInt("mtlId")
//            val mtlName = bundle.getString("mtlName")
            batchCode = bundle.getString("batchCode")

            tv_stockName.visibility = View.GONE
            tv_stockAreaName.visibility = View.GONE
            tv_storageRackName.visibility = View.GONE
            tv_stockPosName.visibility = View.GONE
            if(bundle.containsKey("stock") && bundle.getSerializable("stock") != null) {
                val stock = bundle.getSerializable("stock") as Stock
                tv_stockName.visibility = View.VISIBLE
                stockId = stock.fitemId
                tv_stockName.text = Html.fromHtml("仓库：<font color='#6a5acd'>"+stock!!.stockName+"</font>")
            }
            if(bundle.containsKey("stockArea") && bundle.getSerializable("stockArea") != null) {
                val stockArea = bundle.getSerializable("stockArea") as StockArea
                tv_stockAreaName.visibility = View.VISIBLE
                stockAreaId = stockArea.id
                tv_stockAreaName.text = Html.fromHtml("库区：<font color='#6a5acd'>"+stockArea!!.fname+"</font>")
            }
            if(bundle.containsKey("storageRack") && bundle.getSerializable("storageRack") != null) {
                val storageRack = bundle.getSerializable("storageRack") as StorageRack
                tv_storageRackName.visibility = View.VISIBLE
                storageRackId = storageRack.id
                tv_storageRackName.text = Html.fromHtml("货架：<font color='#6a5acd'>"+storageRack!!.fnumber+"</font>")
            }
            if(bundle.containsKey("stockPos") && bundle.getSerializable("stockPos") != null) {
                val stockPos = bundle.getSerializable("stockPos") as StockPosition
                tv_stockPosName.visibility = View.VISIBLE
                stockPositionId = stockPos.id
                tv_stockPosName.text = Html.fromHtml("库位：<font color='#6a5acd'>"+stockPos!!.stockPositionName+"</font>")
            }
        }

        initLoadDatas()
    }


    // 监听事件
    @OnClick(R.id.btn_close, R.id.btn_search)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {
                closeHandler(mHandler)
                context.finish()
            }
            R.id.btn_search -> initLoadDatas()
        }
    }

    private fun initLoadDatas() {
        limit = 1
        listDatas.clear()
        run_okhttpDatas()
    }

    /**
     * 通过okhttp加载数据
     */
    private fun run_okhttpDatas() {
        showLoadDialog("加载中...", false)
        val mUrl = getURL("inventoryNow/findListByMtl")
        val formBody = FormBody.Builder()
                .add("icItemNumberOrName", getValues(et_search).trim())
                .add("icItemId", icItemId.toString())
                .add("stockId", stockId.toString())
                .add("stockAreaId", stockAreaId.toString())
                .add("storageRackId", storageRackId.toString())
                .add("stockPositionId", stockPositionId.toString())
                .add("batchCode", isNULLS(batchCode))
                .add("avbQtyGt0", "1") // 可用数大于0才显示
                .add("limit", limit.toString())
                .add("pageSize", "30")
                .build()

        val request = Request.Builder()
                .addHeader("cookie", session)
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSUCC1)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    mHandler.sendEmptyMessage(UNSUCC1)
                    return
                }
                isNextPage = JsonUtil.isNextPage(result)

                val msg = mHandler.obtainMessage(SUCC1, result)
                Log.e("run_okhttpDatas --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    override fun onRefresh() {
        isRefresh = true
        isLoadMore = false
        initLoadDatas()
    }

    override fun onLoadMore() {
        isRefresh = false
        isLoadMore = true
        limit += 1
        run_okhttpDatas()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            closeHandler(mHandler)
            context.finish()
        }
        return false
    }

    override fun onDestroy() {
        closeHandler(mHandler)
        super.onDestroy()
    }

    companion object {
        private val SUCC1 = 200
        private val UNSUCC1 = 501
    }
}
