package com.example.coolweather.app.activity;

import java.util.ArrayList;
import java.util.List;

import com.example.coolweather.app.R;
import com.example.coolweather.app.model.City;
import com.example.coolweather.app.model.CoolWeatherDB;
import com.example.coolweather.app.model.County;
import com.example.coolweather.app.model.Province;
import com.example.coolweather.app.util.HttpCallbackListener;
import com.example.coolweather.app.util.HttpUtil;
import com.example.coolweather.app.util.Utility;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;



/**
 * 建立ChooseAreaActivity，作为显示Province、City、County列表的Activity，
 * 其逻辑是打开后调用queryProvinces()到数据库加载Province列表，
 * 如果没有就执行queryFromServer方法，开启子进程到服务器查询并保存至数据库，
 * 如果查询成功就回到主线程再次调用queryProvinces();点击上面的省，我们就进入City的查询逻辑，
 * 依次类推，并且在查询时调用进度对话框，最后onBackPressed()方法使程序可以由县退到市再到省再退出。
 * */
public class ChooseAreaActivity extends Activity {

	public static final int LEVEL_PROVINCE = 0;
	public static final int LEVEL_CITY = 1;
	public static final int LEVEL_COUNTY = 2;
	
	private ProgressDialog progressDialog;
	private TextView titleText;
	private ListView listView;
	private ArrayAdapter<String> adapter;
	private CoolWeatherDB coolWeatherDB;
	private List<String> dataList = new ArrayList<String>();
	
	/**
	 * 省列表
	 * */
	private List<Province> provinceList;
	
	/**
	 * 市列表
	 * */
	private List<City> cityList;
	
	/**
	 * 县列表
	 * */
	private List<County> countyList;
	
	/**
	 * 选中的省份
	 * */
	private Province selectedProvince;
	
	/**
	 * 选中的城市
	 * */
	private City selectedCity;
	
	/**
	 * 当前选中的级别
	 * */
	private int currentLevel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("city_selected", false)) {
			Intent intent = new Intent(this, WeatherActivity.class);
			startActivity(intent);
			finish();
			return;
		}
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);//hide title bar
		setContentView(R.layout.choose_area);//load layout
		
		listView = (ListView) findViewById(R.id.list_view);
		titleText = (TextView) findViewById(R.id.title_text);
		
		adapter = new ArrayAdapter<String> (this, android.R.layout.simple_list_item_1, dataList);
		listView.setAdapter(adapter);
		
		coolWeatherDB = CoolWeatherDB.getInstance(this);
		listView.setOnItemClickListener(new OnItemClickListener() {//为ListView设置点击事件

			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int index, long arg3) {
				// TODO Auto-generated method stub
				if (currentLevel == LEVEL_PROVINCE) { //当前是省级,则查询该省点击的城市
					selectedProvince = provinceList.get(index);
					queryCities();
				} else if (currentLevel == LEVEL_CITY) {//市级，则查询该市点击的县城
					selectedCity = cityList.get(index);
					queryCounties();
				} else if (currentLevel == LEVEL_COUNTY) {
					String countyCode = countyList.get(index).getCountyCode();
					Intent intent = new Intent(ChooseAreaActivity.this, WeatherActivity.class);
					intent.putExtra("county_code", countyCode);
					startActivity(intent);
					Log.d("MyTag", "LEVEL_COUNTY");
					finish();
				}
			}
		});
		queryProvince();//加载省级数据
	}

	/**
	 * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器上查询。
	 * */
	private void queryProvince() {
		// TODO Auto-generated method stub
		provinceList = coolWeatherDB.loadProvinces();//从数据库加载省份数据
		if (provinceList.size() >0) {	//如果数据库有数据则从数据库查询
			dataList.clear();
			for (Province province : provinceList) {	//遍历所有省份
				dataList.add(province.getProvinceName());
			}
			adapter.notifyDataSetChanged();//notifyDataSetChanged方法通过一个外部的方法控制如果适配器的内容改变时需要强制调用getView来刷新每个Item的内容
			listView.setSelection(0);
			titleText.setText("中国");
			currentLevel = LEVEL_PROVINCE;
		} else {						
			queryFromServer(null,"province");//如果数据库没有，则从服务器查询
		}
	}

	/**
	 * 查询选中省内所有的城市，优先从数据库查询，如果没有查询到再去服务器上查询
	 * */
	private void queryCities() {
		// TODO Auto-generated method stub
		cityList = coolWeatherDB.loadCities(selectedProvince.getId());
		if (cityList.size() > 0) {
			dataList.clear();
			for (City city : cityList){
				dataList.add(city.getCityName());
			}
			adapter.notifyDataSetChanged();//notifyDataSetChanged方法通过一个外部的方法控制如果适配器的内容改变时需要强制调用getView来刷新每个Item的内容
			listView.setSelection(0);
			titleText.setText(selectedProvince.getProvinceName());
			currentLevel = LEVEL_CITY;
		} else {
			queryFromServer(selectedProvince.getProvinceCode(), "city");//如果数据库没有，则从服务器查询
		}
	}

	/**
	 * 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询。
	 * */
	private void queryCounties() {
		// TODO Auto-generated method stub
		countyList = coolWeatherDB.loadCounties(selectedCity.getId());
		if (countyList.size() >0) {
			dataList.clear();
			for (County county : countyList) {
				dataList.add(county.getCountyName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectedCity.getCityName());
			currentLevel = LEVEL_COUNTY;
		} else {
			queryFromServer(selectedCity.getCityCode(), "county");
		}
	}

	/**
	 * 根据传入的代号和类型从服务器上查询省市县数据。
	 * @param code:代号（省、市、镇的代号）
	 * @param type：类型（省、市、镇）
	 * */
	private void queryFromServer(final String code, final String type) {
		// TODO Auto-generated method stub
		String address;
		if (!TextUtils.isEmpty(code)) {
			address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";
		} else {
			address = "http://www.weather.com.cn/data/list3/city.xml";
		}
		showProgressDialog();
		HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
			
			@Override
			public void onFinish(String response) {
				// TODO Auto-generated method stub
				boolean result = false;
				if ("province".equals(type)) {
					result = Utility.handleProvincesResponse(coolWeatherDB, response);
				} else if ("city".equals(type)) {
					result = Utility.handleCitiesResponse(coolWeatherDB, response, selectedProvince.getId());
				} else if ("county".equals(type)) {
					result = Utility.handleCountiesResponse(coolWeatherDB, response, selectedCity.getId());
				}
				
				if (result) {
					//通过runOnUiThread()方法回到主线程处理逻辑
					runOnUiThread(new Runnable() {
						
						@Override
						public void run() {
							// TODO Auto-generated method stub
							closeProgressDialog();
							if ("province".equals(type)) {
								queryProvince();
							} else if ("city".equals(type)) {
								queryCities();
							} else if ("county".equals(type)) {
								queryCounties();
							}
						}
					});
				}
			}
			
			@Override
			public void onError(Exception e) {
				// TODO Auto-generated method stub
				//通过runOnUiThread方法回到主线程处理逻辑
				runOnUiThread(new Runnable() {
					
					@Override
					public void run() {
						// TODO Auto-generated method stub
						closeProgressDialog();
						Toast.makeText(ChooseAreaActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
					}
				});
			}
		});
	}

	/**
	 * 显示进度对话框
	 * */
	private void showProgressDialog() {
		// TODO Auto-generated method stub
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("正在加载...");
			//定义一个对话框对象为dialog，点击屏幕其他地方，dialog对话框会消失,为false不消失
			progressDialog.setCanceledOnTouchOutside(false);
		}
		progressDialog.show();
	}

	/**
	 * 关闭进度对话框
	 * */
	private void closeProgressDialog() {
		// TODO Auto-generated method stub
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
	}

	
	/**
	 * 捕获Back按键，根据当前的级别来判断，此时应该返回市列表、省列表、还是直接退出。
	 * */
	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub
		//super.onBackPressed();
		if (currentLevel == LEVEL_COUNTY) {
			queryCities();
		} else if (currentLevel == LEVEL_CITY) {
			queryProvince();
		} else {
			finish();
		}
	}
	
	
}

































