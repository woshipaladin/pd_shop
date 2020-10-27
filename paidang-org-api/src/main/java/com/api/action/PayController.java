package com.api.action;

import com.alipay.api.internal.util.AlipaySignature;
import com.api.MEnumError;
import com.base.action.CoreController;
import com.base.api.ApiException;
import com.base.pay.MFramePayType;
import com.base.pay.PayPropertySupport;
import com.base.pay.ali.AlipayConfig;
import com.base.pay.ali.AlipayNotify;
import com.base.pay.wx.WxPrepay;
import com.base.pay.wx.util.ConstantUtil;
import com.base.pay.wx.util.XMLUtil;
import com.item.dao.model.PayLog;
import com.item.service.BaseService;
import com.paidang.dao.model.OrgBalanceLog;
import com.paidang.dao.model.PawnContinue;
import com.paidang.dao.model.PlatformBalanceLog;
import com.paidang.dao.model.UserPawn;
import com.paidang.service.OrgBalanceLogService;
import com.paidang.service.PawnContinueService;
import com.paidang.service.PlatformBalanceLogService;
import com.paidang.service.UserPawnService;
import com.unionpay.acp.sdk.LogUtil;
import com.unionpay.acp.sdk.SDKConstants;
import com.unionpay.acp.sdk.SDKUtil;
import com.util.MPaidangPayType;
import com.util.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;

@Controller("orgPayControll")
public class PayController extends CoreController {
	
	private static final String[] PAY_PLAT = new String[]{"支付宝","微信"};

	private static final String WEIXIN_RETURN_SUCCESS = "<xml><return_code><![CDATA[SUCCESS]]></return_code></xml>";

	private static final String WEIXIN_RETURN_FAIL = "<xml><return_code><![CDATA[FAIL]]></return_code></xml>";


	@Autowired
	private com.item.service.PayLogService payLogService;
	@Autowired
	private BaseService baseService;
	@Autowired
	private PlatformBalanceLogService platformBalanceLogService;
	@Autowired
	private OrgBalanceLogService orgBalanceLogService;
	@Autowired
	private UserPawnService userPawnService;
	@Autowired
	private PawnContinueService pawnContinueService;


	@RequestMapping(value = "/payReturn")
	public String alipayWapReturn(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logger.info("$$$alipayWapReturn:进入");
		try {
			// 获取支付宝GET过来反馈信息
			Map<String, String> params = new HashMap<String, String>();
			Map requestParams = request.getParameterMap();
			for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
				String name = (String) iter.next();
				String[] values = (String[]) requestParams.get(name);
				String valueStr = "";
				for (int i = 0; i < values.length; i++) {
					valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
				}
				params.put(name, valueStr);
			}

			// 商户订单号
			String out_trade_no = params.get("out_trade_no");
			// 支付宝交易号
			String trade_no = params.get("trade_no");
			// 交易状态
			String trade_status = params.get("trade_status");
			// 自定义参数
			String extraParam = "";
			int offset = out_trade_no.indexOf("_");
			if(offset>-1){
				extraParam = out_trade_no.substring(offset+1);
				out_trade_no = out_trade_no.substring(0,offset);
			}
			// 计算得出通知验证结果
			boolean verify_result = AlipayNotify.verify(params);
			if (verify_result) {// 验证成功
				if (trade_status.equals("TRADE_FINISHED") || trade_status.equals("TRADE_SUCCESS")) {
					logger.info("$$$alipayWapReturn验证成功-开始进行关闭跳转extraParam:"+extraParam);
					if(MFramePayType.RECHARGE.name().equals(extraParam)){
						return "redirect:/m/home/myTicket#/1";
					}else{
						return "redirect:/m/order";
					}
				}
			} else {
				logger.info("$$$$alipayWapReturn验证失败extraParam:"+extraParam+"——商户订单号:" + out_trade_no + ";支付宝交易号:" + trade_no + ",交易状态:" + trade_status);
				response.setContentType("text/html;charset=utf-8");
				response.getWriter().println("验证失败");
			}
		} catch (Exception e) {
			logger.error("$$$$alipayWapReturn异常:" + e.getMessage(), e);
			try {
				response.setContentType("text/html;charset=utf-8");
				response.getWriter().println("alipayWapReturn异常");
			} catch (IOException e1) {
			}
		}
		return null;
	}

	@RequestMapping(value = "/payReturnWeb")
	public String alipayWebReturn(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logger.info("$$$$alipayWebReturn进入!");

		try {
			// 获取支付宝GET过来反馈信息
			Map<String, String> params = new HashMap<String, String>();
			Map requestParams = request.getParameterMap();
			for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
				String name = (String) iter.next();
				String[] values = (String[]) requestParams.get(name);
				String valueStr = "";
				for (int i = 0; i < values.length; i++) {
					valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
				}
				// 乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
				// valueStr = new String(valueStr.getBytes("ISO-8859-1"),
				// "utf-8");
				params.put(name, valueStr);
			}

			// 获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以下仅供参考)//
			// 商户订单号
			String out_trade_no = params.get("out_trade_no");
			// 支付宝交易号
			String trade_no = params.get("trade_no");
			// 交易状态
			String trade_status = params.get("trade_status");
			// 计算得出通知验证结果
			boolean verify_result = AlipayNotify.verify(params);
			String extraParam = "";
			int offset = out_trade_no.indexOf("_");
			if(offset>-1){
				extraParam = out_trade_no.substring(offset+1);
				out_trade_no = out_trade_no.substring(0,offset);
			}
			if (verify_result) {// 验证成功
				if (trade_status.equals("TRADE_FINISHED") || trade_status.equals("TRADE_SUCCESS")) {
					logger.info("$$$$alipayWebReturn验证成功-开始进行关闭跳转extraParam:"+extraParam);
					if(MFramePayType.RECHARGE.name().equals(extraParam)){
						return "redirect:/web/userCenter/cashCoupon";
					}else{
						return "redirect:/web/userCenter/order";
					}
				}
			} else {
				logger.info("$$$$alipayWebReturn验证失败extraParam:"+extraParam+"——商户订单号:" + out_trade_no + ";支付宝交易号:" + trade_no + ",交易状态:" + trade_status);
				response.setContentType("text/html;charset=utf-8");
				response.getWriter().println("验证失败");
			}
		} catch (Exception e) {
			logger.info("$$$$alipayWebReturn异常:" + e.getMessage(), e);
			try {
				response.setContentType("text/html;charset=utf-8");
				response.getWriter().println("alipayWebReturn异常");
			} catch (IOException e1) {
			}
		}
		return null;
	}

	@RequestMapping(value = "/aliPayNotify")
	public void alipayNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logger.info("$$$$alipayNotify进入!");
		try {
			// 获取支付宝POST过来反馈信息
			Map<String, String> params = new HashMap<String, String>();
			Map<String, String[]> requestParams = request.getParameterMap();
			for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext();) {
				String name = iter.next();
				String[] values = (String[]) requestParams.get(name);
				String valueStr = "";
				for (int i = 0; i < values.length; i++) {
					valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
				}
				params.put(name, valueStr);
			}
			// 买家付款账号
			String buyerEmail = params.get("buyer_email");
			BigDecimal total = new BigDecimal(params.get("total_fee"));
			// 商户订单号
			String out_trade_no = params.get("out_trade_no");
			// 支付宝交易号
			String trade_no = params.get("trade_no");
			// 交易状态
			String trade_status = params.get("trade_status");
			String extraParam = "";
			int offset = out_trade_no.indexOf("_");
			if(offset>-1){
				extraParam = out_trade_no.substring(offset+1);
				out_trade_no = out_trade_no.substring(0,offset);
			}

			// 获得支付信息
			String payKey = AlipayConfig.ali_public_key;
//			boolean flag = AlipaySignature.rsaCheckV1(params, payKey, AlipayConfig.input_charset,"RSA2");
			if (AlipayNotify.verifyMobileNotify(params, payKey)) {// 验证成功
//			if (flag){
				if (trade_status.equals("TRADE_FINISHED") || trade_status.equals("TRADE_SUCCESS")) {
					// 支付成功后执行相关业务
					afterPay(out_trade_no, trade_no, trade_status, buyerEmail, total, 1, extraParam);
				}
			} else {
				logger.info("$$$$alipayNotify验证失败——商户订单号:" + out_trade_no + ";支付宝交易号:" + trade_no + ",交易状态:" + trade_status);
			}
			ResponseUtils.renderText(response, "success");
		} catch (Exception e) {
			logger.info("$$$$alipayNotify业务逻辑异常:" + e.getMessage(), e);
			ResponseUtils.renderText(response, "fail");
		}
	}

	@RequestMapping(value = "/aliPayNotifyV2")
	public void alipayNotifyV2(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logger.info("$$$$alipayNotifyV2进入!");
		try {
			// 获取支付宝POST过来反馈信息
			Map<String, String> params = new HashMap<String, String>();
			Map<String, String[]> requestParams = request.getParameterMap();
			for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext();) {
				String name = iter.next();
				String[] values = (String[]) requestParams.get(name);
				String valueStr = "";
				for (int i = 0; i < values.length; i++) {
					valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
				}
				params.put(name, valueStr);
			}
			// 买家付款账号
			String buyerEmail = params.get("buyer_email");
			BigDecimal total = new BigDecimal(params.get("total_fee"));
			// 商户订单号
			String out_trade_no = params.get("out_trade_no");
			// 支付宝交易号
			String trade_no = params.get("trade_no");
			// 交易状态
			String trade_status = params.get("trade_status");
			String extraParam = "";
			int offset = out_trade_no.indexOf("_");
			if(offset>-1){
				extraParam = out_trade_no.substring(offset+1);
				out_trade_no = out_trade_no.substring(0,offset);
			}
			// 获得支付信息
			String payKey = PayPropertySupport.getProperty("pay.ali.publicKey");
			boolean flag = AlipaySignature.rsaCheckV1(params, payKey, AlipayConfig.input_charset,"RSA2");
			if (flag) {// 验证成功
//			if (flag){
				if (trade_status.equals("TRADE_FINISHED") || trade_status.equals("TRADE_SUCCESS")) {
					// 支付成功后执行相关业务
					afterPay(out_trade_no, trade_no, trade_status, buyerEmail, total, 1, extraParam);
				}
			} else {
				logger.info("$$$$alipayNotify验证失败——商户订单号:" + out_trade_no + ";支付宝交易号:" + trade_no + ",交易状态:" + trade_status);
			}
			ResponseUtils.renderText(response, "success");
		} catch (Exception e) {
			logger.info("$$$$alipayNotify业务逻辑异常:" + e.getMessage(), e);
			ResponseUtils.renderText(response, "failure");
		}
	}

	@RequestMapping(value = "/upmpWapNotify", method = RequestMethod.POST)
	public String upmpWapReturn(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		logger.info("@@@@收到upmpWebNotify通知信息,进入notify流程@@@@");

		try {
			req.setCharacterEncoding("ISO-8859-1");
			String encoding = req.getParameter(SDKConstants.param_encoding);
			Map<String, String> respParam = getAllRequestParam(req);
			// 打印请求报文
			LogUtil.printRequestLog(respParam);

			Map<String, String> valideData = null;
			if (null != respParam && !respParam.isEmpty()) {
				Iterator<Entry<String, String>> it = respParam.entrySet().iterator();
				valideData = new HashMap<String, String>(respParam.size());
				while (it.hasNext()) {
					Entry<String, String> e = it.next();
					String key = (String) e.getKey();
					String value = (String) e.getValue();
					value = new String(value.getBytes("ISO-8859-1"), encoding);
					valideData.put(key, value);
				}
			}
			if (!SDKUtil.validate(valideData, encoding)) {
				LogUtil.writeLog("@@@验证签名结果[失败].");
			} else {
				LogUtil.writeLog("@@@验证签名结果[成功].");
			}
			LogUtil.writeLog("@@@upmpWebNotify前台接收报文返回结束");
			
			// 自定义参数
			String extraParam = respParam.get("reqReserved");
			if(MFramePayType.RECHARGE.name().equals(extraParam)){
				return "redirect:/m/home/myTicket#/1";
			}else{
				return "redirect:/m/order";
			}
		} catch (Exception e) {
			LogUtil.writeLog("@@@upmpWebNotify前台接收报文返回失败");
		}
		return null;
	}

	@RequestMapping(value = "/upmpWebNotify", method = RequestMethod.POST)
	public String upmpWebReturn(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		logger.info("@@@@收到upmpWebNotify通知信息,进入notify流程@@@@");

		try {
			req.setCharacterEncoding("ISO-8859-1");
			String encoding = req.getParameter(SDKConstants.param_encoding);
			String pageResult = "/web/userCenter/order";
			Map<String, String> respParam = getAllRequestParam(req);

			// 打印请求报文
			LogUtil.printRequestLog(respParam);

			Map<String, String> valideData = null;
			if (null != respParam && !respParam.isEmpty()) {
				Iterator<Entry<String, String>> it = respParam.entrySet().iterator();
				valideData = new HashMap<String, String>(respParam.size());
				while (it.hasNext()) {
					Entry<String, String> e = it.next();
					String key = (String) e.getKey();
					String value = (String) e.getValue();
					value = new String(value.getBytes("ISO-8859-1"), encoding);
					valideData.put(key, value);
				}
			}
			if (!SDKUtil.validate(valideData, encoding)) {
				LogUtil.writeLog("@@@验证签名结果[失败].");
			} else {
				LogUtil.writeLog("@@@验证签名结果[成功].");
			}
			LogUtil.writeLog("@@@upmpWebNotify前台接收报文返回结束");
			
			// 自定义参数
			String extraParam = respParam.get("reqReserved");
			if(MFramePayType.RECHARGE.name().equals(extraParam)){
				return "redirect:/web/userCenter/cashCoupon";
			}else{
				return "redirect:/web/userCenter/order";
			}
		} catch (Exception e) {
			LogUtil.writeLog("@@@upmpWebNotify前台接收报文返回失败");
		}
		return null;
	}

	@RequestMapping(value = "/upmpMobileNotify")
	public void upmpNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
		LogUtil.writeLog("@@@@upmpMobileNotify接收后台通知开始");

		try {

			request.setCharacterEncoding("ISO-8859-1");
			String encoding = request.getParameter(SDKConstants.param_encoding);
			// 获取请求参数中所有的信息
			Map<String, String> reqParam = getAllRequestParam(request);
			// 打印请求报文
			LogUtil.printRequestLog(reqParam);

			Map<String, String> valideData = null;
			if (null != reqParam && !reqParam.isEmpty()) {
				Iterator<Entry<String, String>> it = reqParam.entrySet().iterator();
				valideData = new HashMap<String, String>(reqParam.size());
				while (it.hasNext()) {
					Entry<String, String> e = it.next();
					String key = (String) e.getKey();
					String value = (String) e.getValue();
					value = new String(value.getBytes("ISO-8859-1"), encoding);
					valideData.put(key, value);
				}
			}

			// 商户订单号
			String out_trade_no = new String(request.getParameter("orderId").getBytes("ISO-8859-1"), "UTF-8");
			// 查询流水号
			String trade_no = new String(request.getParameter("queryId").getBytes("ISO-8859-1"), "UTF-8");
			// 响应码
			String respCode = new String(request.getParameter("respCode").getBytes("ISO-8859-1"), "UTF-8");
			// 交易状态
			String transStatus = new String(request.getParameter("respMsg").getBytes("ISO-8859-1"), "UTF-8");
			BigDecimal total = new BigDecimal(valideData.get("txnAmt")).divide(new BigDecimal(100));
			// 自定义参数
			String extraParam = new String(request.getParameter("reqReserved").getBytes("ISO-8859-1"), "UTF-8");
			// 验证签名
			if (!SDKUtil.validate(valideData, encoding)) {
				LogUtil.writeLog("@@@@验证签名结果[失败].");
			} else {
				LogUtil.writeLog("@@@@验证签名结果[成功].");
				if (null != transStatus && transStatus.equals("Success!")) {
					afterPay(out_trade_no, trade_no, transStatus, "", total, 3, extraParam);
				} else {
					logger.info("@@@@支付交易状态未知——订单号:" + out_trade_no + ";响应码:" + respCode + ";银联查询流水号:" + trade_no + ",交易状态:" + transStatus);
				}

			}

			LogUtil.writeLog("BackRcvResponse接收后台通知结束");
			ResponseUtils.renderText(response, "success");
		} catch (Exception e) {
			logger.info("$$$$支付后业务逻辑异常:" + e.getMessage(), e);
			ResponseUtils.renderText(response, "fail");
		}

	}

	@RequestMapping("/wxMobileNotify")
	public void wxNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logger.info("@@@@收到微信支付信息,进入notify流程@@@@");
		try {
			InputStream in = request.getInputStream();
			String s = null;
			BufferedReader br = new BufferedReader(new InputStreamReader(in, "utf-8"));
			StringBuffer sb = new StringBuffer();
			while ((s = br.readLine()) != null) {
				sb.append(s);
			}
			br.close();
			in.close();
			Map<String, String> params = XMLUtil.doXMLParse(sb.toString());

			SortedMap<String, String> newParams = new TreeMap<String, String>(params);
			
			newParams.put("key", ConstantUtil.PAY_KEY);

			String out_trade_no = (String) params.get("out_trade_no");

			String trade_no = (String) params.get("transaction_id");
			// 总金额，分
			String total = (String) params.get("total_fee");

			String respCode = (String) params.get("result_code");

			String openId = (String) params.get("openid");
			// 自定义参数
			String extraParam = (String) params.get("attach");
			if (WxPrepay.isValiSign(newParams)) {
				logger.info("@@@@验证成功@@@@");
				if (respCode != null && respCode.equals("SUCCESS")) {
					afterPay(out_trade_no, trade_no, respCode, openId, new BigDecimal(total).divide(new BigDecimal(100)), 2, extraParam);
				} else {
					logger.info("@@@@支付交易状态未知——订单号:" + out_trade_no + ";交易状态:" + respCode + ";微信支付订单号:" + trade_no);
				}
			} else {
				logger.info("@@@@验证失败@@@@");
			}
			response.setContentType("text/xml;charset=utf-8");
			response.getWriter().println(WEIXIN_RETURN_SUCCESS);
		} catch (Exception e) {
			logger.info("@@@@支付后业务逻辑异常" + e.getMessage() + "@@@@");
			response.setContentType("text/xml;charset=utf-8");
			response.getWriter().println(WEIXIN_RETURN_FAIL);
		}

	}

	private void afterPay(String out_trade_no, String trade_no, String trade_status, String buyerEmail, BigDecimal total,
			Integer payPlatform, String extraParam) throws Exception {
		
		if (MPaidangPayType.ORG_RATE.name().equals(extraParam)) {
			afterOrgPay(out_trade_no, trade_no, trade_status, buyerEmail, total, payPlatform);
		} else if(MPaidangPayType.ORG_CONTINUE.name().equals(extraParam)){
			afterOrgContinuePay(out_trade_no, trade_no, trade_status, buyerEmail, total, payPlatform);
		}
	}

	/**
	 * 充值回掉
	 * @param out_trade_no
	 * @param trade_no
	 * @param trade_status
	 * @param buyerEmail
	 * @param total
	 * @param payPlatform
	 * @throws Exception
	 */
	private void afterOrgPay(String out_trade_no, String trade_no, String trade_status, String buyerEmail, BigDecimal total,
			Integer payPlatform) throws Exception {
		synchronized (this) {
			logger.info("进入{}典当手续费支付回调",PAY_PLAT[payPlatform-1]);
			PayLog payLog = null;
			// 重新查找订单状态信息
			BigDecimal tradeStatus = null;
			Integer userId = null;
			try {
				payLog = payLogService.selectByPrimaryKey(Long.parseLong(out_trade_no));
				tradeStatus = new BigDecimal(payLog.getState());
				userId = payLog.getUserId();
			} catch (Exception e) {
				logger.info("$$$$支付更新没找到记录或找到多条!——商户订单号:" + out_trade_no + ";交易号:" + trade_no + ",交易状态:" + trade_status);
			}

			if (tradeStatus.intValue() == 0) {
				payLog.setState(9);
				payLog.setModifyTime(new Date());
				payLog.setPlatform(payPlatform); // 1支付宝2银联3微信
				payLog.setRealPay(total);
				payLog.setTradeNo(trade_no);
				payLog.setBuyer(buyerEmail);
				payLogService.updateByPrimaryKeySelective(payLog);

				tradeStatus = new BigDecimal(9);
			}

			if (tradeStatus.intValue() == 9) {
				if (total.compareTo(payLog.getMoney()) < 0) {
					payLog.setState(-1);
					payLogService.updateByPrimaryKeySelective(payLog);
					return;
				}

			UserPawn pawn = userPawnService.selectByPrimaryKey(payLog.getOrderId());
			if (pawn == null){
				throw new ApiException(MEnumError.CONTENT_NOEXIST_ERROR);
			}

			//修改平台服务费支付状态
			pawn.setPlatformState(1);
			userPawnService.updateByPrimaryKey(pawn);

			PlatformBalanceLog platformBalanceLog = new PlatformBalanceLog();
			platformBalanceLog.setAmount(total);
			platformBalanceLog.setInfo("机构"+pawn.getOrgId()+"支付典当id="+pawn.getId()+"的典当平台服务费"+total);
		//	platformBalanceLog.setItem("");
			platformBalanceLog.setType(1);//1-典当 2-续当
			platformBalanceLog.setFid(pawn.getId());//典当id
			platformBalanceLog.setPayLogId(payLog.getId());
			platformBalanceLogService.insert(platformBalanceLog);

			OrgBalanceLog orgBalanceLog = new OrgBalanceLog();
			orgBalanceLog.setOrgId(pawn.getOrgId());
			orgBalanceLog.setMoney(total);
			orgBalanceLog.setType(1);//1支出2收入
			orgBalanceLog.setItem("5");//1用户典当  2用户续当 3用户赎当 4卖给平台 5典当平台服务费 6续当平台服务费
			orgBalanceLog.setInfo("机构"+pawn.getOrgId()+"支付典当id="+pawn.getId()+"的典当平台服务费"+total);
			orgBalanceLog.setPawnCode(pawn.getPawnTicketCode());
			orgBalanceLog.setFid(pawn.getId());
			orgBalanceLog.setPayLogId(payLog.getId());
			orgBalanceLog.setTradeType(2);//0余额1支付宝2微信10线下银行卡
			orgBalanceLog.setTradeNo(trade_no);//支付宝微信流水号
			orgBalanceLog.setUserId(pawn.getUserId());
			orgBalanceLog.setUserName(pawn.getUserName());
			orgBalanceLog.setUserPhone(pawn.getUserPhone());
			orgBalanceLogService.insert(orgBalanceLog);
//				RechargeOrder order = rechargeOrderService.selectByPrimaryKey(payLog.getOrderId());
//				if (order == null){
//					logger.info("$$$$业务订单没有找到!——订单号:" + payLog.getOrderId() + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//					return;
//				}
//
//				if (order.getState() != 0){
//					logger.info("$$$$业务订单状态错误!——订单号:" + payLog.getOrderId() + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//					return;
//				}
//
//				int update = baseService.updateNumById("b_user", "balance", total, userId);
//
//				if (update > 0){
//					// 余额日志
//					UserBalanceLog balanceLog = new UserBalanceLog();
//					balanceLog.setAmount(total);
//					balanceLog.setItem("充值");
//					balanceLog.setInfo("充值金额:"+toString());
//					balanceLog.setType(1);
//					balanceLog.setUserId(userId);
//					balanceLog.setTradeType(payPlatform);
//					balanceLogService.insert(balanceLog);
//				}else{
//					logger.info("$$$$业务订单没有更新成功!——商户订单号:" + out_trade_no + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//					return;
//				}
//
				payLog.setState(10);
				payLogService.updateByPrimaryKeySelective(payLog);

//				order.setTradeNo(trade_no);
//				order.setBuyer(buyerEmail);
//				order.setRealPay(total);
//				order.setPlatform(payPlatform);
//				order.setState(1);
//				update = rechargeOrderService.updateByPrimaryKey(order);
//
//				if (update == 0) {
//					logger.info("$$$$业务订单没有更新成功!——商户订单号:" + out_trade_no + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//				}

			}
		}
	}

	/**
	 * 充值回掉
	 * @param out_trade_no
	 * @param trade_no
	 * @param trade_status
	 * @param buyerEmail
	 * @param total
	 * @param payPlatform
	 * @throws Exception
	 */
	private void afterOrgContinuePay(String out_trade_no, String trade_no, String trade_status, String buyerEmail, BigDecimal total,
							 Integer payPlatform) throws Exception {
		synchronized (this) {
			logger.info("进入{}续当手续费支付回调",PAY_PLAT[payPlatform-1]);
			PayLog payLog = null;
			// 重新查找订单状态信息
			BigDecimal tradeStatus = null;
			Integer userId = null;
			try {
				payLog = payLogService.selectByPrimaryKey(Long.parseLong(out_trade_no));
				tradeStatus = new BigDecimal(payLog.getState());
				userId = payLog.getUserId();
			} catch (Exception e) {
				logger.info("$$$$支付更新没找到记录或找到多条!——商户订单号:" + out_trade_no + ";交易号:" + trade_no + ",交易状态:" + trade_status);
			}

			if (tradeStatus.intValue() == 0) {
				payLog.setState(9);
				payLog.setModifyTime(new Date());
				payLog.setPlatform(payPlatform); // 1支付宝2银联3微信
				payLog.setRealPay(total);
				payLog.setTradeNo(trade_no);
				payLog.setBuyer(buyerEmail);
				payLogService.updateByPrimaryKeySelective(payLog);

				tradeStatus = new BigDecimal(9);
			}

			if (tradeStatus.intValue() == 9) {
				if (total.compareTo(payLog.getMoney()) < 0) {
					payLog.setState(-1);
					payLogService.updateByPrimaryKeySelective(payLog);
					return;
				}

			PawnContinue pawnContinue = pawnContinueService.selectByPrimaryKey(payLog.getOrderId());
			if (pawnContinue == null){
				throw new ApiException(MEnumError.CONTENT_NOEXIST_ERROR);
			}
			//原首次典当信息
			UserPawn userPawn = userPawnService.selectByPrimaryKey(pawnContinue.getPawnId());
			if (userPawn == null){
				throw new ApiException(MEnumError.CONTENT_NOEXIST_ERROR);
			}

			//修改续当平台服务费
			pawnContinue.setPlatformState(1);
			pawnContinueService.updateByPrimaryKey(pawnContinue);

			PlatformBalanceLog platformBalanceLog = new PlatformBalanceLog();
			platformBalanceLog.setAmount(total);
			platformBalanceLog.setInfo("机构"+userPawn.getOrgId()+"支付续当id="+pawnContinue.getId()+"的续当平台服务费"+total);
		//	platformBalanceLog.setItem("");
			platformBalanceLog.setType(2);//1-典当 2-续当
			platformBalanceLog.setFid(pawnContinue.getId());//续当id
			platformBalanceLog.setPayLogId(payLog.getId());
			platformBalanceLogService.insert(platformBalanceLog);

			OrgBalanceLog orgBalanceLog = new OrgBalanceLog();
			orgBalanceLog.setOrgId(userPawn.getOrgId());
			orgBalanceLog.setMoney(total);
			orgBalanceLog.setType(1);//1支出2收入
			orgBalanceLog.setItem("6");//1用户典当  2用户续当 3用户赎当 4卖给平台 5典当平台服务费 6续当平台服务费
			orgBalanceLog.setInfo("机构"+userPawn.getOrgId()+"支付续当id="+pawnContinue.getId()+"的续当平台服务费"+total);
			orgBalanceLog.setPawnCode(pawnContinue.getPawnTicketCode());
			orgBalanceLog.setFid(pawnContinue.getId());
			orgBalanceLog.setPayLogId(payLog.getId());
			orgBalanceLog.setTradeType(2);//0余额1支付宝2微信10线下银行卡
			orgBalanceLog.setTradeNo(trade_no);//支付宝微信流水号
			orgBalanceLog.setUserId(userPawn.getUserId());
			orgBalanceLog.setUserName(pawnContinue.getUserName());
			orgBalanceLog.setUserPhone(userPawn.getUserPhone());
			orgBalanceLogService.insert(orgBalanceLog);
//				RechargeOrder order = rechargeOrderService.selectByPrimaryKey(payLog.getOrderId());
//				if (order == null){
//					logger.info("$$$$业务订单没有找到!——订单号:" + payLog.getOrderId() + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//					return;
//				}
//
//				if (order.getState() != 0){
//					logger.info("$$$$业务订单状态错误!——订单号:" + payLog.getOrderId() + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//					return;
//				}
//
//				int update = baseService.updateNumById("b_user", "balance", total, userId);
//
//				if (update > 0){
//					// 余额日志
//					UserBalanceLog balanceLog = new UserBalanceLog();
//					balanceLog.setAmount(total);
//					balanceLog.setItem("充值");
//					balanceLog.setInfo("充值金额:"+toString());
//					balanceLog.setType(1);
//					balanceLog.setUserId(userId);
//					balanceLog.setTradeType(payPlatform);
//					balanceLogService.insert(balanceLog);
//				}else{
//					logger.info("$$$$业务订单没有更新成功!——商户订单号:" + out_trade_no + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//					return;
//				}
//
				payLog.setState(10);
				payLogService.updateByPrimaryKeySelective(payLog);
//
//				order.setTradeNo(trade_no);
//				order.setBuyer(buyerEmail);
//				order.setRealPay(total);
//				order.setPlatform(payPlatform);
//				order.setState(1);
//				update = rechargeOrderService.updateByPrimaryKey(order);
//
//				if (update == 0) {
//					logger.info("$$$$业务订单没有更新成功!——商户订单号:" + out_trade_no + ";交易号:" + trade_no + ",交易状态:" + trade_status);
//				}

			}
		}
	}

	
	public static Map<String, String> getAllRequestParam(final HttpServletRequest request) {
		Map<String, String> res = new HashMap<String, String>();
		Enumeration<?> temp = request.getParameterNames();
		if (null != temp) {
			while (temp.hasMoreElements()) {
				String en = (String) temp.nextElement();
				String value = request.getParameter(en);
				res.put(en, value);
				// 在报文上送时，如果字段的值为空，则不上送<下面的处理为在获取所有参数数据时，判断若值为空，则删除这个字段>
				// System.out.println("ServletUtil类247行  temp数据的键=="+en+"     值==="+value);
				if (null == res.get(en) || "".equals(res.get(en))) {
					res.remove(en);
				}
			}
		}
		return res;
	}
}
