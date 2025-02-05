package com.paidang.service;


import com.base.util.DateUtil;
import com.base.util.LogKit;
import com.base.util.StringUtil;
import com.paidang.dao.ExpressMapper;
import com.paidang.dao.OrderMapper;
import com.paidang.dao.UserGoodsMapper;
import com.paidang.dao.model.Express;
import com.paidang.dao.model.ExpressExample;
import com.paidang.dao.model.UserGoods;
import com.paidang.daoEx.ExpressMapperEx;
import com.paidang.daoEx.model.ExpressEx;
import com.util.PaidangConst;
import com.util.express.KuaidiApiUtil;
import com.util.express.core.KuaidiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ExpressService {
	@Autowired
	private ExpressMapper expressMapper;
	@Autowired
	private ExpressMapperEx expressMapperEx;
	@Autowired
	private UserGoodsMapper userGoodsMapper;
	@Autowired
	private OrderService orderService;
	@Autowired
	private UserGoodsService userGoodsService;
	@Autowired
	private OrderMapper orderMapper;

	public int countByExample(ExpressExample example) {
		return this.expressMapper.countByExample(example);
	}

	public Express selectByPrimaryKey(Integer id) {
		return this.expressMapper.selectByPrimaryKey(id);
	}

	public List<Express> selectByExample(ExpressExample example) {
		return this.expressMapper.selectByExample(example);
	}

	public List<Express> selectByExampleWithBLOBs(ExpressExample example) {
		return this.expressMapper.selectByExampleWithBLOBs(example);
	}

	public int deleteByPrimaryKey(Integer id) {
		return this.expressMapper.deleteByPrimaryKey(id);
	}

	public int updateByPrimaryKeySelective(Express record) {
		return this.expressMapper.updateByPrimaryKeySelective(record);
	}

	public int updateByPrimaryKeyWithBLOBs(Express record) {
		return this.expressMapper.updateByPrimaryKeyWithBLOBs(record);
	}

	public int updateByPrimaryKey(Express record) {
		return this.expressMapper.updateByPrimaryKey(record);
	}

	public int deleteByExample(ExpressExample example) {
		return this.expressMapper.deleteByExample(example);
	}

	public int updateByExampleSelective(Express record, ExpressExample example) {
		return this.expressMapper.updateByExampleSelective(record, example);
	}

	public int updateByExampleWithBLOBs(Express record, ExpressExample example) {
		return this.expressMapper.updateByExampleWithBLOBs(record, example);
	}

	public int updateByExample(Express record, ExpressExample example) {
		return this.expressMapper.updateByExample(record, example);
	}

	public int insert(Express record) {
		return this.expressMapper.insert(record);
	}

	public int insertSelective(Express record) {
		return this.expressMapper.insertSelective(record);
	}

	/**
	 * 未签收快递
	 * @return
	 */
	public List<Express> selectUnReceive(){
		ExpressExample example = new ExpressExample();
		//example.or().andExpressStateNotBetween(3,4).andModifyTimeLessThanOrEqualTo(DateUtil.addMinute(new Date(),-PaidangConst.EXPRESS_QUERY_TIME));
		example.or().andExpressStateNotBetween(3,4)
				.andModifyTimeGreaterThanOrEqualTo(DateUtil.addMinute(new Date(),-PaidangConst.EXPRESS_QUERY_TIME))
				.andModifyTimeLessThanOrEqualTo(new Date());
		example.or().andExpressStateNotBetween(3,4).andModifyTimeIsNull();
		example.setOrderByClause("create_time desc");
		return this.expressMapper.selectByExample(example);
	}

	/**
	 * 定时查询
	 */
	public void queryAuto(){
		List<Express> expresses = this.selectUnReceive();
		for (Express express : expresses){
			//以下一句add by laria at 20190927
			if(StringUtil.isNotBlank(express.getExpressCode())){
				LogKit.info("定时任务-ExpressCancle：物流单号--"+express.getExpressCode() );
				KuaidiResult result = KuaidiApiUtil.query(express.getExpressCode());
				LogKit.info("定时任务-ExpressCancle：物流单号--"+express.getExpressCode() + ",物流查询结果："+ result);
				express.setModifyTime(new Date());
				if (result != null){
					express.setExpressState(result.getState());
					express.setExpressData(result.toString());
					if (result.getState() == 3){
						//已签收
						expressOk(express);
					}
				}
				//更新快递状态
				expressMapper.updateByPrimaryKeySelective(express);
			}
		}
		LogKit.info("定时任务-ExpressCancle结束" );
	}

	/**
	 * 特殊接口：根据单号，不管状态，强制设置成已签收的业务状态。非紧急情况不可调用
	 * @param expressCode
	 */
	public void queryAutoSpecialAllToSuccess(String expressCode){
		ExpressExample example = new ExpressExample();
		example.createCriteria().andExpressCodeEqualTo(expressCode).andExpressStateNotBetween(3,4);
		example.setOrderByClause("create_time desc");
		List<Express> expresses = this.expressMapper.selectByExample(example);
		for (Express express : expresses){
			KuaidiResult result = KuaidiApiUtil.query(express.getExpressCode());
			express.setModifyTime(new Date());
			if (result != null){
				express.setExpressData(result.toString());
			}
			//强制设置成签收
			express.setExpressState(3);
			//已签收
			expressOk(express);
			//更新快递状态
			expressMapper.updateByPrimaryKeySelective(express);
		}
	}


	public void expressOk(Express express){
		switch (express.getType()){
			case 1:
				//确认平台收货
				UserGoods post = new UserGoods();
				post.setId(express.getFid());
				post.setPostState(4);
				post.setLocation(1);
				userGoodsMapper.updateByPrimaryKeySelective(post);
				break;
			case 2:
				//确认取回
				UserGoods back = new UserGoods();
				back.setId(express.getFid());
				back.setBackState(3);
				back.setLocation(0);
				userGoodsMapper.updateByPrimaryKeySelective(back);
				break;
			case 3:
				//商城
				orderService.confirmOrder(express.getFid());
				break;
//			case 6:
//				Order record = new Order();
//				record.setPlatOrgState(3);
//				goodsMapper.updateByPrimaryKeySelective(record);
//				break;
			default:
				break;
		}
	}

	public Express queryByCode(String code){
		ExpressExample expressExample = new ExpressExample();
		expressExample.createCriteria().andExpressCodeEqualTo(code);
		List<Express> list = expressMapper.selectByExample(expressExample);
		return list.size() > 0? list.get(0):null;
	}

	public List<ExpressEx> selectByGoods(Map<String, Object> map) {
		return this.expressMapperEx.selectByGoods(map);
	}

	//搜索
	public List<ExpressEx> searchByName(String keyword,String orgId){
		return  expressMapperEx.searchByName(keyword,orgId);
	}

	//查询机构从平台处取回它的绝当品的快递数据，并更新对应字段
	public void updateOrgFetchBackExpress(){
		ExpressExample expressExample = new ExpressExample();
		expressExample.createCriteria().andTypeEqualTo(6);//所有的机构申请取回快递
		List<Express> expresses = this.selectByExample(expressExample);
		for (Express express:expresses) {
			KuaidiResult result = KuaidiApiUtil.query(express.getExpressCode());
			if (result != null){
				express.setExpressState(result.getState());
				express.setExpressData(result.toString());
				if (result.getState() == 3){//如果快递查询接口出来表明机构已经签收，顺带更新p_user_goods表的对应状态
					UserGoods record = userGoodsService.selectByPrimaryKey(express.getFid());
					record.setPlatOrgState(3);
					userGoodsService.updateByPrimaryKey(record);
				}
			}
			expressMapper.updateByPrimaryKeySelective(express);
		}
	}

	//更新快递表机构/平台寄给当户的数据，并更新相应字段
	public void updateSendToWinnerExpress(){
		ExpressExample expressExample = new ExpressExample();
		expressExample.createCriteria().andTypeIn(new ArrayList<Integer>(){{add(4);add(5);}});//所有寄给当户的快递记录
		List<Express> expresses = this.selectByExample(expressExample);
		for (Express express:expresses) {
			KuaidiResult result = KuaidiApiUtil.query(express.getExpressCode());
			if (result != null){
				express.setExpressState(result.getState());
				express.setExpressData(result.toString());
			}
			expressMapper.updateByPrimaryKeySelective(express);
		}
	}


	public void updateData(Map<String, Object> map) {
		expressMapperEx.updateData(map);
	}
}
