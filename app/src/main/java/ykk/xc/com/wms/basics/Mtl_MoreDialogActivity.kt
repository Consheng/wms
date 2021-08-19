package ykk.xc.com.wms.basics

import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import butterknife.OnClick
import kotlinx.android.synthetic.main.ab_mtl_list_more.*
import okhttp3.*
import ykk.xc.com.wms.R
import ykk.xc.com.wms.basics.adapter.Mtl_MoreDialogAdapter
import ykk.xc.com.wms.bean.Stock
import ykk.xc.com.wms.bean.StockArea
import ykk.xc.com.wms.bean.StockPosition
import ykk.xc.com.wms.bean.StorageRack
import ykk.xc.com.wms.bean.k3Bean.ICItem
import ykk.xc.com.wms.comm.BaseDialogActivity
import ykk.xc.com.wms.comm.Comm
import ykk.xc.com.wms.util.JsonUtil
import ykk.xc.com.wms.util.basehelper.BaseRecyclerAdapter
import ykk.xc.com.wms.util.xrecyclerview.XRecyclerView
import java.io.IOException
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.*

/**
 * 选择多个物料dialog
 */
class Mtl_MoreDialogActivity : BaseDialogActivity(), XRecyclerView.LoadingListener {

    companion object {
        private val SUCC1 = 200
        private val UNSUCC1 = 501
    }
    private val context = this
    private val listDatas = ArrayList<ICItem>()
    private var mAdapter: Mtl_MoreDialogAdapter? = null
    private val okHttpClient = OkHttpClient()
    private var limit = 1
    private var isRefresh: Boolean = false
    private var isLoadMore: Boolean = false
    private var isNextPage: Boolean = false
    private var isICInvBackUp = 0 // 是否查询盘点的物料
    private var strMtlId:String? = null // 拼接的物料id
    private var stock :Stock? = null                     // 盘点的仓库
    private var stockArea :StockArea? = null            // 盘点的库区
    private var storageRack :StorageRack? = null        // 盘点的货架
    private var stockPos :StockPosition? = null    // 盘点的库位
    private var clickStock = 0
    private var clickStockArea = 0
    private var clickStorageRack = 0
    private var clickStockPos = 0

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Mtl_MoreDialogActivity) : Handler() {
        private val mActivity: WeakReference<Mtl_MoreDialogActivity>

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
                        val list = JsonUtil.strToList2(msg.obj as String, ICItem::class.java)
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
        return R.layout.ab_mtl_list_more
    }

    override fun initView() {
        xRecyclerView!!.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        xRecyclerView!!.layoutManager = LinearLayoutManager(context)
        mAdapter = Mtl_MoreDialogAdapter(context, listDatas)
        xRecyclerView!!.adapter = mAdapter
        xRecyclerView!!.setLoadingListener(context)

        xRecyclerView!!.isPullRefreshEnabled = false // 上啦刷新禁用
        xRecyclerView.setLoadingMoreEnabled(false); // 不显示下拉刷新的view

        mAdapter!!.onItemClickListener = BaseRecyclerAdapter.OnItemClickListener { adapter, holder, view, pos ->
            val m = listDatas[pos - 1]
            val isCheck = m.isCheck()
            if (isCheck) {
                m.isCheck = false
            } else {
                m.isCheck = true
            }
            mAdapter!!.notifyDataSetChanged()
        }
    }

    override fun initData() {
        val bundle = context.intent.extras
        if (bundle != null) {
            isICInvBackUp = bundle.getInt("isICInvBackUp")
            if(isICInvBackUp > 0) {
                lin_stockGroup.visibility = View.VISIBLE
            }
            strMtlId = bundle.getString("strMtlId")
            if(bundle.containsKey("stock")) stock = bundle.getSerializable("stock") as Stock
            if(bundle.containsKey("stockArea")) stockArea = bundle.getSerializable("stockArea") as StockArea
            if(bundle.containsKey("storageRack")) storageRack = bundle.getSerializable("storageRack") as StorageRack
            if(bundle.containsKey("stockPos")) stockPos = bundle.getSerializable("stockPos") as StockPosition
            if(stock != null) { // 默认选中仓库
                clickStock = 1
                btn_stock.text = stock!!.stockName
                btn_stock.visibility = View.VISIBLE
                btn_stock.setTextColor(Color.parseColor("#FFFFFF"))
                btn_stock.setBackgroundResource(R.drawable.shape_purple1a)
            }
            if(stockArea != null) {
                btn_stockArea.text = stockArea!!.fname
                btn_stockArea.visibility = View.VISIBLE
            }
            if(storageRack != null) {
                btn_storageRack.text = storageRack!!.fnumber
                btn_storageRack.visibility = View.VISIBLE
            }
            if(stockPos != null) {
                btn_stockPos.text = stockPos!!.stockPositionName
                btn_stockPos.visibility = View.VISIBLE
            }
        }

        initLoadDatas()
    }


    // 监听事件
    @OnClick(R.id.btn_close, R.id.btn_search, R.id.btn_confirm, R.id.btn_stock, R.id.btn_stockArea, R.id.btn_storageRack, R.id.btn_stockPos)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {
                closeHandler(mHandler)
                context.finish()
            }
            R.id.btn_search -> initLoadDatas()
            R.id.btn_confirm -> {// 确认
                val size = listDatas.size
                if (size == 0) {
                    Comm.showWarnDialog(context, "请查询数据！")
                    return
                }
                val listMtl = ArrayList<ICItem>()
                for (i in 0 until size) {
                    val mtl = listDatas[i]
                    if (mtl.isCheck()) {
                        listMtl.add(mtl)
                    }
                }
                if (listMtl.size == 0) {
                    Comm.showWarnDialog(context, "请至少选择一行数据！")
                    return
                }
                val intent = Intent()
                intent.putExtra("obj", listMtl as Serializable)
                context.setResult(RESULT_OK, intent)
                context.finish()
            }
            R.id.btn_stock -> {
                clickStock = 1
                clickStockArea = 0
                clickStorageRack = 0
                clickStockPos = 0
                clickAfterChangeStatus()
                initLoadDatas()
            }
            R.id.btn_stockArea -> {
                clickStock = 1
                clickStockArea = 1
                clickStorageRack = 0
                clickStockPos = 0
                clickAfterChangeStatus()
                initLoadDatas()
            }
            R.id.btn_storageRack -> {
                clickStock = 1
                clickStockArea = 1
                clickStorageRack = 1
                clickStockPos = 0
                clickAfterChangeStatus()
                initLoadDatas()
            }
            R.id.btn_stockPos -> {
                clickStock = 1
                clickStockArea = 1
                clickStorageRack = 1
                clickStockPos = 1
                clickAfterChangeStatus()
                initLoadDatas()
            }
        }
    }

    /**
     * 点击仓库按钮之后改变状态
     */
    private fun clickAfterChangeStatus() {
        // 先初始化
        btn_stock.setTextColor(Color.parseColor("#666666"))
        btn_stock.setBackgroundResource(R.drawable.back_style_blue_gray)
        btn_stockArea.setTextColor(Color.parseColor("#666666"))
        btn_stockArea.setBackgroundResource(R.drawable.back_style_blue_gray)
        btn_storageRack.setTextColor(Color.parseColor("#666666"))
        btn_storageRack.setBackgroundResource(R.drawable.back_style_blue_gray)
        btn_stockPos.setTextColor(Color.parseColor("#666666"))
        btn_stockPos.setBackgroundResource(R.drawable.back_style_blue_gray)

        if(clickStock > 0) {
            btn_stock.setTextColor(Color.parseColor("#FFFFFF"))
            btn_stock.setBackgroundResource(R.drawable.shape_purple1a)
        }
        if(clickStockArea > 0) {
            btn_stockArea.setTextColor(Color.parseColor("#FFFFFF"))
            btn_stockArea.setBackgroundResource(R.drawable.shape_purple1a)
        }
        if(clickStorageRack > 0) {
            btn_storageRack.setTextColor(Color.parseColor("#FFFFFF"))
            btn_storageRack.setBackgroundResource(R.drawable.shape_purple1a)
        }
        if(clickStockPos > 0) {
            btn_stockPos.setTextColor(Color.parseColor("#FFFFFF"))
            btn_stockPos.setBackgroundResource(R.drawable.shape_purple1a)
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
        val mUrl = getURL("icItem/findListByPage")

        val stockId2 = if(clickStock > 0 && stock != null) stock!!.id.toString() else ""
        val stockAreaId2 = if(clickStockArea > 0 && stockArea != null) stockArea!!.id.toString() else ""
        val storageRackId2 = if(clickStorageRack > 0 && storageRack != null) storageRack!!.id.toString() else ""
        val stockPosId2 = if(clickStockPos > 0 && stockPos != null) stockPos!!.id.toString() else ""
        val formBody = FormBody.Builder()
                .add("fNumberAndName", getValues(et_search).trim())
                // 查询盘点的物料
                .add("isICInvBackUp", isICInvBackUp.toString())
                .add("stockId", stockId2)
                .add("stockAreaId", stockAreaId2)
                .add("storageRackId", storageRackId2)
                .add("stockPosId", stockPosId2)
                .add("strMtlId", if(strMtlId != null) strMtlId else "")

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

}
