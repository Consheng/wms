package ykk.xc.com.wms.warehouse

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import butterknife.OnClick
import kotlinx.android.synthetic.main.ware_disassembly.*
import okhttp3.*
import ykk.xc.com.wms.R
import ykk.xc.com.wms.basics.*
import ykk.xc.com.wms.bean.*
import ykk.xc.com.wms.bean.k3Bean.ICInventory
import ykk.xc.com.wms.bean.k3Bean.ICItem
import ykk.xc.com.wms.bean.k3Bean.Inventory_K3
import ykk.xc.com.wms.comm.BaseActivity
import ykk.xc.com.wms.comm.BaseFragment
import ykk.xc.com.wms.comm.Comm
import ykk.xc.com.wms.util.BigdecimalUtil
import ykk.xc.com.wms.util.JsonUtil
import ykk.xc.com.wms.util.LogUtil
import ykk.xc.com.wms.util.basehelper.BaseRecyclerAdapter
import ykk.xc.com.wms.util.zxing.android.CaptureActivity
import ykk.xc.com.wms.warehouse.adapter.Ware_Disassembly_Adapter
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 调拨申请
 */
class Ware_Disassembly_Activity : BaseActivity() {

    companion object {
        private val SEL_DEPT = 61
        private val SEL_MTL = 62
        private val SEL_STOCK = 63
        private val SEL_STOCKPOS = 64
        private val SUCC1 = 200
        private val UNSUCC1 = 500
        private val SUCC2 = 201
        private val UNSUCC2 = 501
        private val SAVE = 202
        private val UNSAVE = 502
        private val PASS = 203
        private val UNPASS = 503

        private val SETFOCUS = 1
        private val SAOMA = 2
        private val WRITE_CODE = 3
        private val RESULT_NUM = 4
    }
    private val context = this
    private var mAdapter: Ware_Disassembly_Adapter? = null
    private var okHttpClient: OkHttpClient? = null
    private var user: User? = null
    private var timesTamp:String? = null // 时间戳
    private val checkDatas = ArrayList<ICChangeEntry>()
    private var isTextChange: Boolean = false // 是否进入TextChange事件
    private var curPos = -1
    private var dept :Department? = null
    private var stock :Stock? = null
    private var stockPos :StockPosition? = null
    private var fid :String? = null // 保存成功，返回的内码id

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Ware_Disassembly_Activity) : Handler() {
        private val mActivity: WeakReference<Ware_Disassembly_Activity>

        init {
            mActivity = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            val m = mActivity.get()
            if (m != null) {
                m.hideLoadDialog()

                var errMsg: String? = null
                var msgObj: String? = null
                if (msg.obj is String) {
                    msgObj = msg.obj as String
                }
                when (msg.what) {
                    SUCC1 -> { // 扫描成功
                        val list = JsonUtil.strToList(msgObj, ICChangeEntry::class.java)
                        m.checkDatas.clear()
                        m.checkDatas.addAll(list)
                        m.mAdapter!!.notifyDataSetChanged()

                        m.run_findInventoryQty(list[0])
                    }
                    UNSUCC1 -> { // 数据加载失败！
                        m.tv_icItemName.text = ""
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有加载到数据！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    SUCC2 -> { // 查询库存 进入
                        val list = JsonUtil.strToList(msgObj, Inventory_K3::class.java)
                        m.checkDatas.forEach {
                            if(it.fmtrlType == 0) {
                                it.inventoryQty = list[0].fqty
                            }
                        }
                        m.mAdapter!!.notifyDataSetChanged()
                    }
                    UNSUCC2 -> { // 查询库存  失败
                        m.checkDatas.forEach {
                            if(it.fmtrlType == 0) {
                                it.inventoryQty = 0.0
                            }
                        }
                        m.mAdapter!!.notifyDataSetChanged()
                    }
                    SAVE -> { // 保存成功 进入
                        m.fid = JsonUtil.strToString(msgObj)
                        m.timesTamp = m.user!!.getId().toString() + "-" + Comm.randomUUID()
                        m.toasts("保存成功")
                        m.lin_mtl.visibility = View.GONE
                        m.isTextChange = true
                        m.btn_save.visibility = View.GONE
                        m.btn_pass.visibility = View.VISIBLE
                    }
                    UNSAVE -> { // 保存失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "保存失败！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    PASS -> { // 审核成功 进入
                        m.timesTamp = m.user!!.getId().toString() + "-" + Comm.randomUUID()
                        m.toasts("审核成功")
                        m.reset()
                    }
                    UNPASS -> { // 审核失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "审核失败！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    SETFOCUS -> { // 当弹出其他窗口会抢夺焦点，需要跳转下，才能正常得到值
                        m.setFocusable(m.et_getFocus)
                        m.setFocusable(m.et_code)
                    }
                    SAOMA -> { // 扫码之后
                        if(m.checkDatas.size > 0) {
                            Comm.showWarnDialog(m.context,"请先保存数据！")
                            return
                        }
                        // 执行查询方法
                        m.run_smDatas(null)
                    }
                }
            }
        }

    }

    override fun setLayoutResID(): Int {
        return R.layout.ware_disassembly
    }

    override fun initView() {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    //                .connectTimeout(10, TimeUnit.SECONDS) // 设置连接超时时间（默认为10秒）
                    .writeTimeout(120, TimeUnit.SECONDS) // 设置写的超时时间
                    .readTimeout(120, TimeUnit.SECONDS) //设置读取超时时间
                    .build()
        }

        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        recyclerView.layoutManager = LinearLayoutManager(context)
        mAdapter = Ware_Disassembly_Adapter(context, checkDatas)
        recyclerView.adapter = mAdapter
        // 设值listview空间失去焦点
        recyclerView.isFocusable = false

        // 行事件输入数量
        mAdapter!!.onItemClickListener = BaseRecyclerAdapter.OnItemClickListener { adapter, holder, view, pos ->
            if(parseInt(fid) == 0 && pos > -1) {
                curPos = pos
                showInputDialog("数量", checkDatas[pos].fqty.toString(), "0.0", RESULT_NUM)
            }
        }

        // 长按选择仓库
        mAdapter!!.onItemLongClickListener = BaseRecyclerAdapter.OnItemLongClickListener { adapter, holder, view, pos ->
            if(parseInt(fid) == 0 && pos > -1) {
                curPos = pos
                showForResult(Stock_DialogActivity::class.java, SEL_STOCK, null)
            }
        }
    }

    override fun initData() {
        getUserInfo()
        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
//        hideSoftInputMode(et_positionCode)
        hideSoftInputMode(et_code)
        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
    }


    // 监听事件
    @OnClick(R.id.btn_close, R.id.tv_deptSel, R.id.btn_mtlSel, R.id.btn_scan, R.id.tv_icItemName, R.id.btn_reset, R.id.btn_save, R.id.btn_pass)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {
                closeHandler(mHandler)
                context.finish()
            }
            R.id.tv_deptSel -> { // 选择部门
                showForResult(Dept_DialogActivity::class.java, SEL_DEPT, null)
            }
            R.id.btn_mtlSel -> { // 选择物料
                if(checkDatas.size > 0) {
                    Comm.showWarnDialog(context,"请先保存数据！")
                    return
                }
                val bundle = Bundle()
                showForResult(Mtl_BomDialogActivity::class.java, SEL_MTL, bundle)
            }
            R.id.btn_scan -> { // 调用摄像头
                if(checkDatas.size > 0) {
                    Comm.showWarnDialog(context,"请先保存数据！")
                    return
                }
                showForResult(CaptureActivity::class.java, BaseFragment.CAMERA_SCAN, null)
            }
            R.id.tv_icItemName -> { // 物料点击
                mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
            }
            R.id.btn_reset -> { // 重置
                if (checkDatas.size > 0) {
                    val build = AlertDialog.Builder(context)
                    build.setIcon(R.drawable.caution)
                    build.setTitle("系统提示")
                    build.setMessage("您有未保存的数据，继续重置吗？")
                    build.setPositiveButton("是") { dialog, which -> reset() }
                    build.setNegativeButton("否", null)
                    build.setCancelable(false)
                    build.show()

                } else {
                    reset()
                }
            }
            R.id.btn_save -> { // 保存
                if(dept == null) {
                    Comm.showWarnDialog(context, "请选择部门！")
                    return
                }
                val size = checkDatas.size
                if (size == 0) {
                    Comm.showWarnDialog(context, "请扫描或选择物料信息！")
                    return
                }
                checkDatas.forEachIndexed { index, it ->
                    if(it.fqty <= 0) {
                        Comm.showWarnDialog(context, "第"+(index+1)+"行，数量必须大于0！")
                        return
                    }
                }
                val icChange = ICChange()
                icChange.ffee = 0.0
                icChange.fcurrencyID = 1
                icChange.fexchangeRate = 1.0
                icChange.fbillerID = user!!.erpUserId
                icChange.fempID = user!!.empId
                icChange.fstatus = 0
                icChange.fdeptID = dept!!.fitemID
                icChange.fexchangeRateType = 1
                icChange.createUserId = user!!.id
                icChange.createUserName = user!!.username
                run_save(JsonUtil.objectToString(icChange))
            }
            R.id.btn_pass -> { // 审核
                if(parseInt(fid) == 0) {
                    Comm.showWarnDialog(context,"还没有保存，不能审核！")
                    return
                }
                run_pass()
            }
        }
    }

    private fun reset() {
        fid = null
        lin_mtl.visibility = View.VISIBLE
        isTextChange = false
        btn_save.visibility = View.VISIBLE
        btn_pass.visibility = View.GONE
        et_code.setText("")
        tv_icItemName.text = ""
        checkDatas.clear()
        mAdapter!!.notifyDataSetChanged()
        mHandler.sendEmptyMessage(SETFOCUS)
    }

    override fun setListener() {
        val click = View.OnClickListener { v ->
            setFocusable(et_getFocus)
            when (v.id) {
                R.id.et_code -> setFocusable(et_code)
            }
        }
        et_code.setOnClickListener(click)

        // 物料---数据变化
        et_code!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.length == 0) return
                if (!isTextChange) {
                    isTextChange = true
                    mHandler.sendEmptyMessageDelayed(SAOMA, 300)
                }
            }
        })
        // 物料---长按输入条码
        et_code!!.setOnLongClickListener {
            showInputDialog("输入条码号", getValues(et_code), "none", WRITE_CODE)
            true
        }
        // 物料---焦点改变
        et_code.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if(hasFocus) {
                lin_focusMtl.setBackgroundResource(R.drawable.back_style_red_focus)
            } else {
                if (lin_focusMtl != null) {
                    lin_focusMtl.setBackgroundResource(R.drawable.back_style_gray4)
                }
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            // 当选择蓝牙的时候按了返回键
            if (data == null) return
            when (requestCode) {
                SEL_DEPT -> {   // 部门返回
                    dept = data!!.getSerializableExtra("obj") as Department
                    tv_deptSel.text = dept!!.departmentName
                }
                SEL_STOCK -> {  // 仓库返回
                    stock = data!!.getSerializableExtra("obj") as Stock
                    /*if(stock!!.fisStockMgr == 1) {
                        val bundle = Bundle()
                        bundle.putInt("stockId", if(stock != null)stock!!.id else 0)
                        showForResult(StockPos_DialogActivity::class.java, SEL_STOCKPOS, bundle)
                    } else { }
                    */
                    stock!!.fname = stock!!.stockName
                    checkDatas[curPos].fstockID = stock!!.fitemId
                    checkDatas[curPos].fsPID = 0
                    checkDatas[curPos].stock = stock
                    checkDatas[curPos].stockPos = null
                    mAdapter!!.notifyDataSetChanged()
                    if(checkDatas[curPos].fmtrlType == 0) {
                        run_findInventoryQty(checkDatas[0])
                    }
                }
                /*SEL_STOCKPOS -> {  // 库位返回
                    stockPos = data!!.getSerializableExtra("obj") as StockPosition

                    stock!!.fname = stock!!.stockName
                    stockPos!!.fname = stockPos!!.stockPositionName
                    checkDatas[curPos].fstockID = stock!!.fitemId
                    checkDatas[curPos].fsPID = stockPos!!.fitemId
                    checkDatas[curPos].stock = stock
                    checkDatas[curPos].stockPos = stockPos
                    mAdapter!!.notifyDataSetChanged()
                }*/
                BaseFragment.CAMERA_SCAN -> {// 扫一扫成功  返回
                    val bundle = data!!.extras
                    if (bundle != null) {
                        val code = bundle.getString(BaseFragment.DECODED_CONTENT_KEY, "")
                        setTexts(et_code, code)
                    }
                }
                SEL_MTL -> { //查询物料	返回
                    val icItem = data!!.getSerializableExtra("obj") as ICItem
                    run_smDatas(icItem.fitemid.toString())
                }
                WRITE_CODE -> {// 输入条码  返回
                    val bundle = data!!.extras
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        /*when (smqFlag) {
                            '1' -> setTexts(et_positionCode, value.toUpperCase())
                            '2' -> setTexts(et_code, value.toUpperCase())
                        }*/
                        setTexts(et_code, value.toUpperCase())
                    }
                }
                RESULT_NUM -> { // 数量	返回
                    val bundle = data!!.getExtras()
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        val num = parseDouble(value)
                        val icChangeEntry = checkDatas[curPos]
                        if(icChangeEntry.fmtrlType == 0) {
                            checkDatas.forEach {
                                if(it.fmtrlType == 1) {
                                    val mulVal = BigdecimalUtil.mul(num, it.fauxQty_Base)
                                    val divVal = BigdecimalUtil.div(mulVal, it.bom_FQty, it.icItem.fqtydecimal)
                                    it.fqty = divVal
                                    it.fbaseQty = divVal
                                } else {
                                    it.fqty = num
                                    it.fbaseQty = num
                                }
                            }
                        } else {
                            icChangeEntry.fqty = num
                            icChangeEntry.fbaseQty = num
                        }
                        mAdapter!!.notifyDataSetChanged()
                    }
                }
            }
        }
        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
    }

    /**
     * 扫描查询物料
     */
    private fun run_smDatas(fitemId :String?) {
        isTextChange = false
        showLoadDialog("加载中...")
        var mUrl = getURL("bom/findBarcode")
        val formBody = FormBody.Builder()
                .add("barcode", if(fitemId == null)getValues(et_code) else "")
                .add("fitemId", if(fitemId != null)fitemId else "")
                .build()
        val request = Request.Builder()
                .addHeader("cookie", session)
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSUCC1)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSUCC1, result)
                    mHandler.sendMessage(msg)
                    return
                }


                val msg = mHandler.obtainMessage(SUCC1, result)
                Log.e("run_smDatas --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 查询库存
     */
    private fun run_findInventoryQty(entry :ICChangeEntry) {
        val mUrl = getURL("inventory_K3/findInventoryQty")
        val formBody = FormBody.Builder()
                .add("fStockID", entry.fstockID.toString())
                .add("fStockPlaceID",  entry.fsPID.toString())
                .add("mtlId", entry.fitemID.toString())
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSUCC2)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                LogUtil.e("run_findInventoryQty --> onResponse", result)
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSUCC2, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(SUCC2, result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 保存
     */
    private fun run_save(strICChange :String) {
        showLoadDialog("保存中...")
        val mUrl = getURL("icChange/save")
        val formBody = FormBody.Builder()
                .add("strICChange", strICChange)
                .add("strICChangeEntry", JsonUtil.objectToString(checkDatas))
                .add("timesTamp", timesTamp)
                .build()

        val request = Request.Builder()
                .addHeader("cookie", session)
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSAVE)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSAVE, result)
                    mHandler.sendMessage(msg)
                    return
                }

                val msg = mHandler.obtainMessage(SAVE, result)
                Log.e("run_save --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 审核
     */
    private fun run_pass() {
        showLoadDialog("审核中...")
        val mUrl = getURL("icChange/pass")
        val formBody = FormBody.Builder()
                .add("fid", fid)
                .build()

        val request = Request.Builder()
                .addHeader("cookie", session)
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNPASS)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNPASS, result)
                    mHandler.sendMessage(msg)
                    return
                }

                val msg = mHandler.obtainMessage(PASS, result)
                Log.e("run_pass --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 得到用户对象
     */
    private fun getUserInfo() {
        if (user == null) user = showUserByXml()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 按了删除键，回退键
        //        if(!isKeyboard && (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL || event.getKeyCode() == KeyEvent.KEYCODE_DEL)) {
        // 240 为PDA两侧面扫码键，241 为PDA中间扫码键
        return if (!(event.keyCode == 240 || event.keyCode == 241)) {
            false
        } else super.dispatchKeyEvent(event)
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
