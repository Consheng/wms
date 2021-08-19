package ykk.xc.com.wms.produce

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import butterknife.OnClick
import kotlinx.android.synthetic.main.prod_in_stock_fragment1.*
import kotlinx.android.synthetic.main.prod_in_stock_main.*
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import ykk.xc.com.wms.R
import ykk.xc.com.wms.basics.Dept_DialogActivity
import ykk.xc.com.wms.basics.Emp_DialogActivity
import ykk.xc.com.wms.basics.Stock_DialogActivity
import ykk.xc.com.wms.bean.*
import ykk.xc.com.wms.bean.k3Bean.Emp
import ykk.xc.com.wms.comm.BaseFragment
import ykk.xc.com.wms.comm.Comm
import ykk.xc.com.wms.util.JsonUtil
import ykk.xc.com.wms.util.LogUtil
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

/**
 * 日期：2019-10-16 09:50
 * 描述：生产入库
 * 作者：ykk
 */
class Prod_InStock_Fragment1 : BaseFragment() {

    companion object {
        private val SEL_DEPT = 10
        private val SEL_EMP1 = 12
        private val SEL_EMP2 = 13
        private val SEL_EMP3 = 14
        private val SEL_EMP4 = 15
        private val SEL_STOCK = 16
        private val RESULT_NUM = 1
        private val SAVE = 202
        private val UNSAVE = 502
        private val FIND_ICSTOCKBILL = 204
        private val UNFIND_ICSTOCKBILL = 504
    }

    private val context = this
    private var okHttpClient: OkHttpClient? = null
    private var user: User? = null
    private var mContext: Activity? = null
    private var parent: Prod_InStock_MainActivity? = null
    private var timesTamp:String? = null // 时间戳
    var icstockBill = ICStockBill() // 保存的对象
    private val df = DecimalFormat("#.###") // 重量保存三位小数
    private var icStockBillId = 0 // 上个页面传来的id
    var saveNeedHint = true // 保存之后需要提示

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Prod_InStock_Fragment1) : Handler() {
        private val mActivity: WeakReference<Prod_InStock_Fragment1>

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
                    SAVE -> {// 保存成功 进入
                        val strId_pdaNo = JsonUtil.strToString(msgObj)
                        if(m.icstockBill.id == 0) {
                            val arr = strId_pdaNo.split(":") // id和pdaNo数据拼接（1:IC201912121）
                            m.icstockBill.id = m.parseInt(arr[0])
                            m.icstockBill.pdaNo = arr[1]
                            m.tv_pdaNo.text = arr[1]
                        }
                        if(m.saveNeedHint) {
                            m.parent!!.isMainSave = true
                            m.parent!!.viewPager.setScanScroll(true); // 放开左右滑动
                            m.toasts("保存成功✔")
                            // 滑动第二个页面
                            m.parent!!.viewPager!!.setCurrentItem(1, false)
                            m.parent!!.isChange = if(m.icStockBillId == 0) true else false
                        }
                        m.saveNeedHint = true
                    }
                    UNSAVE -> { // 保存失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "保存失败！"
                        Comm.showWarnDialog(m.mContext, errMsg)
                    }
                    FIND_ICSTOCKBILL -> { // 查询主表信息 成功
                        val icsBill = JsonUtil.strToObject(msgObj, ICStockBill::class.java)
                        m.setICStockBill(icsBill)
                    }
                    UNFIND_ICSTOCKBILL -> { // 查询主表信息 失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "查询信息有错误！2秒后自动关闭..."
                        Comm.showWarnDialog(m.mContext, errMsg)
                        m.mHandler.postDelayed(Runnable {
                            m.mContext!!.finish()
                        },2000)
                    }
                }
            }
        }
    }

    fun setICStockBill(m : ICStockBill) {
        icstockBill.id = m.id
        icstockBill.pdaNo = m.pdaNo
        icstockBill.fdate = m.fdate
        icstockBill.fsupplyId = m.fsupplyId
        icstockBill.suppName = m.suppName
        icstockBill.fdeptId = m.fdeptId
        icstockBill.fempId = m.fempId
        icstockBill.fsmanagerId = m.fsmanagerId
        icstockBill.fmanagerId = m.fmanagerId
        icstockBill.ffmanagerId = m.ffmanagerId
        icstockBill.fbillerId = m.fbillerId
        icstockBill.fselTranType = m.fselTranType

        icstockBill.suppName = m.suppName
        icstockBill.deptName = m.deptName
        icstockBill.yewuMan = m.yewuMan          // 业务员
        icstockBill.baoguanMan = m.baoguanMan          // 保管人
        icstockBill.fuzheMan = m.fuzheMan           // 负责人
        icstockBill.yanshouMan = m.yanshouMan            // 验收人
        icstockBill.createUserId = m.createUserId        // 创建人id
        icstockBill.createUserName = m.createUserName        // 创建人
        icstockBill.createDate = m.createDate            // 创建日期
        icstockBill.isToK3 = m.isToK3                   // 是否提交到K3
        icstockBill.roughWeight = m.roughWeight            // 毛重
        icstockBill.netWeight = m.netWeight          // 净重
        icstockBill.weightUnitType = m.weightUnitType            // 重量单位类型(1：千克，2：克，3：磅)
        icstockBill.k3Number = m.k3Number                // k3返回的单号
        icstockBill.qualifiedStockId = m.qualifiedStockId       // 合格仓库id
        icstockBill.unQualifiedStockId = m.unQualifiedStockId       // 不合格仓库id
        icstockBill.missionBillId = m.missionBillId

        icstockBill.supplier = m.supplier
        icstockBill.department = m.department
        icstockBill.qualifiedStock = m.qualifiedStock
        icstockBill.unQualifiedStock = m.unQualifiedStock

        tv_pdaNo.text = m.pdaNo
        tv_inDateSel.text = m.fdate
        tv_deptSel.text = m.deptName
        tv_emp2Sel.text = m.baoguanMan
        tv_emp4Sel.text = m.yanshouMan
        tv_roughWeight.text = df.format(m.roughWeight)
        tv_netWeight.text = df.format(m.netWeight)
        // 重量单位类型(1：千克，2：克，3：磅)
        when(m.weightUnitType) {
            1 -> { // 千克（kg）
                tv_weightUnitType.text = "千克（kg）"
            }
            2 -> { // 克（g）
                tv_weightUnitType.text = "克（g）"
            }
            3 -> { // 磅（lb）
                tv_weightUnitType.text = "磅（lb）"
            }
        }

        parent!!.isChange = false
        parent!!.isMainSave = true
        parent!!.viewPager.setScanScroll(true); // 放开左右滑动
        EventBus.getDefault().post(EventBusEntity(12)) // 发送指令到fragment3，查询分类信息
    }

    override fun setLayoutResID(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.prod_in_stock_fragment1, container, false)
    }

    override fun initView() {
        mContext = getActivity()
        parent = mContext as Prod_InStock_MainActivity

    }

    override fun initData() {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    //                .connectTimeout(10, TimeUnit.SECONDS) // 设置连接超时时间（默认为10秒）
                    .writeTimeout(30, TimeUnit.SECONDS) // 设置写的超时时间
                    .readTimeout(30, TimeUnit.SECONDS) //设置读取超时时间
                    .build()
        }

        getUserInfo()
        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        tv_inDateSel.text = Comm.getSysDate(7)
        tv_operationManName.text = user!!.erpUserName
        tv_emp2Sel.text = user!!.empName
        tv_emp4Sel.text = user!!.empName

        icstockBill.billType = "SCRK"
        icstockBill.ftranType = 2
        icstockBill.frob = 1
        icstockBill.fselTranType = 85
        icstockBill.weightUnitType = 1
        icstockBill.fempId = user!!.empId
        icstockBill.yewuMan = user!!.empName
        icstockBill.fsmanagerId = user!!.empId
        icstockBill.baoguanMan = user!!.empName
        icstockBill.fmanagerId = user!!.empId
        icstockBill.fuzheMan = user!!.empName
        icstockBill.ffmanagerId = user!!.empId
        icstockBill.yanshouMan = user!!.empName
        icstockBill.fbillerId = user!!.erpUserId
        icstockBill.createUserId = user!!.id
        icstockBill.createUserName = user!!.username

        bundle()
    }

    fun bundle() {
        val bundle = mContext!!.intent.extras
        if(bundle != null) {
            if(bundle.containsKey("id")) { // 查询过来的
                icStockBillId = bundle.getInt("id") // ICStockBill主表id
                // 查询主表信息
                run_findStockBill(icStockBillId)
            }
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
        }
    }

    @OnClick(R.id.tv_inDateSel, R.id.tv_deptSel, R.id.tv_stockSel, R.id.tv_emp2Sel, R.id.tv_emp4Sel,
             R.id.btn_save, R.id.btn_clone, R.id.tv_weightUnitType, R.id.tv_connBlueTooth, R.id.tv_roughWeight)
    fun onViewClicked(view: View) {
        var bundle: Bundle? = null
        when (view.id) {
            R.id.tv_inDateSel -> { // 选择日期
                Comm.showDateDialog(mContext, tv_inDateSel, 0)
            }
            R.id.tv_deptSel -> { // 选择部门
                showForResult(Dept_DialogActivity::class.java, SEL_DEPT, null)
            }
            R.id.tv_stockSel -> { // 选择仓库
                val bundle = Bundle()
                bundle.putString("accountType", "SC")
                bundle.putInt("unDisable", 1) // 只显示未禁用的数据
                showForResult(Stock_DialogActivity::class.java, SEL_STOCK, bundle)
            }
//            R.id.tv_emp1Sel -> { // 选择业务员
//                bundle = Bundle()
//                bundle.putString("accountType", "SC")
//                showForResult(Emp_DialogActivity::class.java, SEL_EMP1, bundle)
//            }
            R.id.tv_emp2Sel -> { // 选择保管者
                bundle = Bundle()
                bundle.putString("accountType", "SC")
                showForResult(Emp_DialogActivity::class.java, SEL_EMP2, bundle)
            }
//            R.id.tv_emp3Sel -> { // 选择负责人
//                bundle = Bundle()
//                bundle.putString("accountType", "SC")
//                showForResult(Emp_DialogActivity::class.java, SEL_EMP3, bundle)
//            }
            R.id.tv_emp4Sel -> { // 选择验收人
                bundle = Bundle()
                bundle.putString("accountType", "SC")
                showForResult(Emp_DialogActivity::class.java, SEL_EMP4, bundle)
            }
            R.id.tv_weightUnitType -> { // 称重单位选择
                pop_unitType(view)
                popWindow!!.showAsDropDown(view)
            }
            R.id.tv_connBlueTooth -> { // 蓝牙连接
                parent!!.openBluetooth()
            }
            R.id.tv_roughWeight -> { // 输入毛重
                showInputDialog("毛重", "", "0.0", RESULT_NUM)
            }
            R.id.btn_save -> { // 保存
                if(!checkSave()) return
                icstockBill.fdate = getValues(tv_inDateSel)
                icstockBill.roughWeight = parseDouble(getValues(tv_roughWeight))
                run_save()
            }
            R.id.btn_clone -> { // 重置
                if (parent!!.isChange) {
                    val build = AlertDialog.Builder(mContext)
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
        }
    }

    /**
     * 保存检查数据判断
     */
    fun checkSave() : Boolean {
//        if (icstockBill.fdeptId == 0) {
//            Comm.showWarnDialog(mContext, "请选择部门！")
//            return false;
//        }
//        if (icstockBill.stock == null) {
//            Comm.showWarnDialog(mContext, "请选择收料仓库！")
//            return false;
//        }
        if(icstockBill.fsmanagerId == 0) {
            Comm.showWarnDialog(mContext, "请选择保管人！")
            return false
        }
        if(icstockBill.ffmanagerId == 0) {
            Comm.showWarnDialog(mContext, "请选择验收人！")
            return false
        }
        return true;
    }

    override fun setListener() {

    }

    fun reset() {
//        setEnables(tv_deptSel, R.drawable.back_style_blue2, true)
        parent!!.isMainSave = false
        parent!!.viewPager.setScanScroll(false) // 禁止滑动
        tv_pdaNo.text = ""
        tv_inDateSel.text = Comm.getSysDate(7)
        tv_deptSel.text = ""
        tv_stockSel.text = ""
//        tv_emp2Sel.text = ""
//        tv_emp4Sel.text = ""
//        tv_weightUnitType.text = "千克（kg）"
        tv_roughWeight.text = ""
        tv_netWeight.text = ""
        icstockBill.id = 0
        icstockBill.fselTranType = 85
        icstockBill.pdaNo = ""
        icstockBill.fsupplyId = 0
        icstockBill.fdeptId = 0
//        icstockBill.fempId = 0
//        icstockBill.fsmanagerId = 0
//        icstockBill.fmanagerId = 0
//        icstockBill.ffmanagerId = 0
        icstockBill.suppName = ""
        icstockBill.deptName = ""
//        icstockBill.yewuMan = ""
//        icstockBill.baoguanMan = ""
//        icstockBill.fuzheMan = ""
//        icstockBill.yanshouMan = ""
        icstockBill.roughWeight = 0.0
//        icstockBill.weightUnitType = 1
        icstockBill.netWeight = 0.0
        icstockBill.stock = null
        icstockBill.department = null

        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        parent!!.isChange = false
        saveNeedHint = true
        EventBus.getDefault().post(EventBusEntity(11)) // 发送指令到fragment2，告其清空
    }

    /**
     * 创建PopupWindow 【 来源类型选择 】
     */
    private var popWindow: PopupWindow? = null
    private fun pop_unitType(v: View) {
        if (null != popWindow) {//不为空就隐藏
            popWindow!!.dismiss()
            return
        }
        // 获取自定义布局文件popupwindow_left.xml的视图
        val popV = layoutInflater.inflate(R.layout.weight_unitname_popwindow, null)
        // 创建PopupWindow实例,200,LayoutParams.MATCH_PARENT分别是宽度和高度
        popWindow = PopupWindow(popV, v.width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        // 设置动画效果
        // popWindow.setAnimationStyle(R.style.AnimationFade);
        popWindow!!.setBackgroundDrawable(BitmapDrawable())
        popWindow!!.isOutsideTouchable = true
        popWindow!!.isFocusable = true

        // 点击其他地方消失
        val click = View.OnClickListener { v ->
            when (v.id) {
                R.id.tv1 -> { // 千克（kg）
                    tv_weightUnitType.text = "千克（kg）"
                    icstockBill.weightUnitType = 1
                }
                R.id.tv2 -> { // 克（g）
                    tv_weightUnitType.text = "克（g）"
                    icstockBill.weightUnitType = 2
                }
                R.id.tv3 -> { // 磅（lb）
                    tv_weightUnitType.text = "磅（lb）"
                    icstockBill.weightUnitType = 3
                }
            }
            popWindow!!.dismiss()
        }
        popV.findViewById<View>(R.id.tv1).setOnClickListener(click)
        popV.findViewById<View>(R.id.tv2).setOnClickListener(click)
        popV.findViewById<View>(R.id.tv3).setOnClickListener(click)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SEL_DEPT -> {//查询部门	返回
                if (resultCode == Activity.RESULT_OK) {
                    val dept = data!!.getSerializableExtra("obj") as Department
                    if(dept.productStockId == 0) {
                        Comm.showWarnDialog(mContext,"该仓库没有设置成品仓！")
                        return
                    }
                    tv_deptSel.text = dept!!.departmentName
                    icstockBill.fdeptId = dept.fitemID
                    icstockBill.deptName = dept.departmentName
                    icstockBill.department = dept

                }
            }
            SEL_STOCK -> {// 仓库	返回
                if (resultCode == Activity.RESULT_OK) {
                    val stock = data!!.getSerializableExtra("obj") as Stock
                    tv_stockSel.text = stock!!.fname
                    icstockBill.stock = stock
                }
            }
            SEL_EMP2 -> {//查询保管人	返回
                if (resultCode == Activity.RESULT_OK) {
                    val emp = data!!.getSerializableExtra("obj") as Emp
                    tv_emp2Sel.text = emp!!.fname
                    icstockBill.fsmanagerId = emp.fitemId
                    icstockBill.baoguanMan = emp.fname
                }
            }
            SEL_EMP4 -> {//查询验收人	返回
                if (resultCode == Activity.RESULT_OK) {
                    val emp = data!!.getSerializableExtra("obj") as Emp
                    tv_emp4Sel.text = emp!!.fname
                    icstockBill.ffmanagerId = emp.fitemId
                    icstockBill.yanshouMan = emp.fname
                }
            }
            RESULT_NUM -> { // 数量	返回
                if (resultCode == Activity.RESULT_OK) {
                    val bundle = data!!.getExtras()
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        val num = parseDouble(value)
                        tv_roughWeight.text = df.format(num)
                        icstockBill.roughWeight = num
                    }
                }
            }
        }
    }

    /**
     * 保存
     */
    fun run_save() {
        showLoadDialog("保存中...", false)
        val mUrl = getURL("stockBill_WMS/save")

        val mJson = JsonUtil.objectToString(icstockBill)
        val formBody = FormBody.Builder()
                .add("strJson", mJson)
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
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
                LogUtil.e("run_save --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     *  查询主表信息
     */
    private fun run_findStockBill(id: Int) {
        val mUrl = getURL("stockBill_WMS/findStockBill")

        val formBody = FormBody.Builder()
                .add("id", id.toString())
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNFIND_ICSTOCKBILL)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNFIND_ICSTOCKBILL, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(FIND_ICSTOCKBILL, result)
                LogUtil.e("run_missionBillModifyStatus --> onResponse", result)
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

    override fun onDestroyView() {
        closeHandler(mHandler)
        mBinder!!.unbind()
        super.onDestroyView()
    }
}