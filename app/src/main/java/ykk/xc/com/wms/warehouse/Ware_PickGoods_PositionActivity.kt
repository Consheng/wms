package ykk.xc.com.wms.warehouse

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import butterknife.OnClick
import kotlinx.android.synthetic.main.ware_pickgoods_position.*
import okhttp3.*
import ykk.xc.com.wms.R
import ykk.xc.com.wms.bean.ICStockBillEntry
import ykk.xc.com.wms.bean.User
import ykk.xc.com.wms.comm.BaseActivity
import ykk.xc.com.wms.comm.BaseFragment
import ykk.xc.com.wms.comm.Comm
import ykk.xc.com.wms.util.JsonUtil
import ykk.xc.com.wms.util.LogUtil
import ykk.xc.com.wms.util.zxing.android.CaptureActivity
import ykk.xc.com.wms.warehouse.adapter.Ware_PickGoods_Position_Adapter
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * 拣货位置
 */
class Ware_PickGoods_PositionActivity : BaseActivity() {

    companion object {
        private val SUCC1 = 200
        private val UNSUCC1 = 500

        private val SETFOCUS = 1
        private val SAOMA = 2
        private val WRITE_CODE = 3
    }
    private val context = this
    private var okHttpClient: OkHttpClient? = null
    private var mAdapter: Ware_PickGoods_Position_Adapter? = null
    private val listDatas = ArrayList<ICStockBillEntry>()
    private var isTextChange: Boolean = false // 是否进入TextChange事件
    private var listBarcode = ArrayList<String>()
    private val strBarcode = StringBuffer()
    private var user: User? = null

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Ware_PickGoods_PositionActivity) : Handler() {
        private val mActivity: WeakReference<Ware_PickGoods_PositionActivity>

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
                    SUCC1 -> { // 查询分录 进入
                        m.listDatas.clear()
                        val list = JsonUtil.strToList(msgObj, ICStockBillEntry::class.java)
                        m.listDatas.addAll(list)

                        m.mAdapter!!.notifyDataSetChanged()
                    }
                    UNSUCC1 -> { // 查询分录  失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有找到数据！"
                        Comm.showWarnDialog(m.context, errMsg)
                        // 查询失败，就剔除
                        val barcode = m.getValues(m.et_code).trim()
                        if(m.listBarcode.contains(barcode)) {
                            m.listBarcode.remove(barcode)
                            m.strBarcode.setLength(0)
                            m.listBarcode.forEach {
                                m.strBarcode.append(if(m.strBarcode.length > 0) ","+it else it)
                            }
                            m.tv_strBarcode.text = Html.fromHtml("条码:&nbsp;<font color='#6a5acd'>"+m.strBarcode.toString().replace(",", "，")+"</font>")
                        }
                    }
                    SETFOCUS -> { // 当弹出其他窗口会抢夺焦点，需要跳转下，才能正常得到值
                        m.setFocusable(m.et_getFocus)
                        m.setFocusable(m.et_code)
                    }
                    SAOMA -> { // 扫码之后
                        val barcode = m.getValues(m.et_code).trim()
                        if(m.listBarcode.contains(barcode)) {
                            m.isTextChange = false
                            Comm.showWarnDialog(m.context,"该条码已扫描！")
                            return
                        }
                        m.listBarcode.add(barcode)
                        if(m.strBarcode.length > 0) {
                            m.strBarcode.append(","+barcode)
                        } else {
                            m.strBarcode.append(barcode)
                        }
                        m.tv_strBarcode.text = Html.fromHtml("条码:&nbsp;<font color='#6a5acd'>"+m.strBarcode.toString().replace(",", "，")+"</font>")
                        // 执行查询方法
                        m.run_smDatas(m.strBarcode.toString())
                    }
                }
            }
        }
    }

    override fun setLayoutResID(): Int {
        return R.layout.ware_pickgoods_position
    }

    override fun initView() {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    //                .connectTimeout(10, TimeUnit.SECONDS) // 设置连接超时时间（默认为10秒）
                    .writeTimeout(30, TimeUnit.SECONDS) // 设置写的超时时间
                    .readTimeout(30, TimeUnit.SECONDS) //设置读取超时时间
                    .build()
        }

        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        recyclerView.layoutManager = LinearLayoutManager(context)
        mAdapter = Ware_PickGoods_Position_Adapter(context, listDatas)
        recyclerView.adapter = mAdapter
        // 设值listview空间失去焦点
        recyclerView.isFocusable = false
    }

    override fun initData() {
        hideSoftInputMode(et_code)
        mHandler.sendEmptyMessageDelayed(SETFOCUS,200)
        getUserInfo()
    }

    @OnClick(R.id.btn_close, R.id.btn_scan, R.id.btn_clone)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> { // 关闭
                context.finish()
            }
            R.id.btn_scan -> { // 调用摄像头扫描（物料）
                showForResult(CaptureActivity::class.java, BaseFragment.CAMERA_SCAN, null)
            }
            R.id.btn_clone -> { // 重置
                strBarcode.setLength(0)
                tv_strBarcode.text = "条码："
                listBarcode.clear()
                listDatas.clear()
                mAdapter!!.notifyDataSetChanged()
            }
        }
    }

    override fun setListener() {
        val click = View.OnClickListener { v ->
            setFocusable(et_getFocus)
            when (v.id) {
                R.id.et_code -> setFocusable(et_code)
            }
        }
        et_code!!.setOnClickListener(click)

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
                    lin_focusMtl!!.setBackgroundResource(R.drawable.back_style_gray4)
                }
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            // 当选择蓝牙的时候按了返回键
            when (requestCode) {
                BaseFragment.CAMERA_SCAN -> {// 扫一扫成功  返回
                    if (resultCode == Activity.RESULT_OK) {
                        val bundle = data!!.extras
                        if (bundle != null) {
                            val code = bundle.getString(BaseFragment.DECODED_CONTENT_KEY, "")
                            setTexts(et_code, code)
                        }
                    }
                }
            }
        }
        mHandler.sendEmptyMessageDelayed(SETFOCUS,200)
    }

    /**
     * 扫码查询对应的方法
     */
    private fun run_smDatas(strBarcode :String) {
        isTextChange = false
        showLoadDialog("加载中...", false)
        var mUrl = getURL("stockBill_WMS/findPickGoodsStockPosByBarcode")
        val formBody = FormBody.Builder()
                .add("barcode", getValues(et_code).trim())
                .add("strBarcode", strBarcode)
                .add("userName", user!!.username)
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
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
                LogUtil.e("run_smDatas --> onResponse", result)
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSUCC1, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(SUCC1, result)
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

    override fun onDestroy() {
        super.onDestroy()
        closeHandler(mHandler)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 按了删除键，回退键
        //        if(!isKeyboard && (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL || event.getKeyCode() == KeyEvent.KEYCODE_DEL)) {
        // 240 为PDA两侧面扫码键，241 为PDA中间扫码键
        return if (!(event.keyCode == 240 || event.keyCode == 241)) {
            false
        } else super.dispatchKeyEvent(event)
    }


}