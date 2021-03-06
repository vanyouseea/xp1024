package hqr.action;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import hqr.domain.TopicInfo;
import hqr.util.Brower;

public class GetTopic {
	private String host;
	private String startUrl;
	private String lastRunDt;
	private String baseDir;
	private String imgBaseDir;
	private String aria2;
	private String token;
	private SimpleDateFormat yyyyMMddhhmm = new SimpleDateFormat("yyyy-MM-dd hh:mm");
	private String processDt;
	private SendMsg msg;
	private CloseableHttpClient httpclient = Brower.getCloseableHttpClient();
	private HttpClientContext httpClientContext = Brower.getHttpClientContext();
	private boolean isNormalTopicStart = false;
	
	private ArrayList<TopicInfo> topicList = new ArrayList<TopicInfo>();
	
	public GetTopic() {
		this.host = System.getProperty("host");
		this.startUrl = System.getProperty("startUrl");
		this.lastRunDt = System.getProperty("lastRunDt");
		this.baseDir = System.getProperty("baseDir");
		this.imgBaseDir = System.getProperty("imgBaseDir");
		this.aria2 = System.getProperty("aria2");
		this.token = System.getProperty("token");
		
		
		processDt = yyyyMMddhhmm.format(new Date());
		
		msg = new SendMsg(httpclient, httpClientContext);
		
	}
	
	public void execute() {
		try{
			boolean stopIt = false;
			//gogogo start from page 1, util no new cn topic
			for(int i=0;i<9999;i++) {
				if(stopIt) {
					break;
				}
				System.out.println("Process "+startUrl+(i+1));
				HttpGet get = new HttpGet(startUrl+(i+1));
				CloseableHttpResponse cl = httpclient.execute(get, httpClientContext);
				String html = EntityUtils.toString(cl.getEntity(), "UTF-8");
				
				if(cl.getStatusLine().getStatusCode()==200) {
					Document bodys = Jsoup.parse(html);
					//class = tr3 , then select all td
					Element ajaxtable = bodys.getElementById("ajaxtable");
					Elements trs = ajaxtable.select("tr");
					
					for (Element element : trs) {
						//get all td 
						Elements tds = element.select("td");
						
						if(!isNormalTopicStart) {
							if(tds.size()!=0) {
								if("????????????".equals(tds.get(0).html())) {
									isNormalTopicStart = true;
								}
								else {
									continue;
								}
							}
							else {
								continue;
							}
						}
						if(tds.size()==5&&isNormalTopicStart) {
							String topicUrl = host+"/"+tds.get(0).select("a").attr("href");
							String subject = tds.get(1).select("a").html();
							String author = tds.get(2).select("a").html();
							String issueDt = tds.get(4).select("a").html();
							System.out.println("TopicUrl:"+topicUrl+" issueDt:"+issueDt+" Author:"+author);
							//not normal topic,skip it
							if("".equals(author)) {
								continue;
							}
							
							if(compareDateTime(lastRunDt, issueDt)) {	
								if(subject.indexOf("??????")>=0||subject.indexOf("??????")>=0) {
									//save the topic url in the list
									TopicInfo ti = new TopicInfo();
									ti.setTopicUrl(topicUrl);
									ti.setSubject(subject);
									ti.setAuthor(author);
									ti.setIssueDt(issueDt);
									topicList.add(ti);
								}
							}
							else {
								System.out.println("[-]stop to collect topic due to current issueDt:"+issueDt+" < lastRundt:"+lastRunDt);
								updateConfig(issueDt);
								stopIt = true;
								break;
							}
						}
						else {
							//not common topic, skip it
						}
					}
				}
				else {
					String wxMsg = "?????????"+processDt+"\n???????????????????????????\n??????????????? "+host+" ?????????????????????200";
					System.out.println(wxMsg);
					msg.execute(wxMsg);
					System.exit(255);
				}
				cl.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			String wxMsg = "?????????"+processDt+"\n???????????????????????????\n????????????????????? "+host+", ????????????????????????????????????config.dat???????????????";
			System.out.println(wxMsg);
			msg.execute(wxMsg);
			System.exit(255);
		}
		
		//loop the topicList from oldest to newest
		for(int i=topicList.size()-1; i>=0 ;i--) {
			TopicInfo ti = topicList.get(i);
			Grab gb = new Grab(ti.getTopicUrl(), ti.getSubject(), ti.getAuthor(), httpclient, httpClientContext, msg);
			gb.execute();
			//when process 1 topic, update the config prop file's last run date, if job raise error, can rerun simply
			updateConfig(ti.getIssueDt());
		}

		String wxMsg = "";
		if(topicList.size()==0) {
			wxMsg = "?????????"+processDt+"\n???????????????????????????\n???????????????????????????????????????????????????";
		}
		else {
			wxMsg = "?????????"+processDt+"\n???????????????????????????\n??????????????? " +topicList.size()+ " ??????????????????????????????onedrive";
		}
		System.out.println(wxMsg);
		msg.execute(wxMsg);
		
	}
	
	/*
	 * Covert the date from 2021-03-06 15:44 to 20210306
	 */
	private boolean compareDateTime(String lastRunDt, String issueDt) {
		try {
			Date d1 = yyyyMMddhhmm.parse(lastRunDt);
			Date d2 = yyyyMMddhhmm.parse(issueDt);
			
			return d1.before(d2);
			
		} catch (Exception e) {
			String wxMsg = "?????????"+processDt+"\n???????????????????????????\n???????????????????????????????????????????????????????????????????????????????????????????????????????????????";
			System.out.println(wxMsg);
			msg.execute(wxMsg);
			System.exit(255);
			return false;
		}
	}
	
	/*	
	 * Save the property file again with the new lastRunDt
	 */
	private void updateConfig(String dt) {
		Properties newProp = new Properties();
		newProp.setProperty("host", host);
		newProp.setProperty("startUrl", startUrl);
		newProp.setProperty("lastRunDt", dt);
		newProp.setProperty("baseDir", baseDir);
		newProp.setProperty("imgBaseDir", imgBaseDir);
		newProp.setProperty("aria2", aria2);
		newProp.setProperty("token", token);
		newProp.setProperty("skipIfExist", System.getProperty("skipIfExist"));
		newProp.setProperty("corpid", System.getProperty("corpid"));
		newProp.setProperty("corpsecret", System.getProperty("corpsecret"));
		newProp.setProperty("agentid", System.getProperty("agentid"));
		
		try {
			newProp.store(new FileWriter("config.dat"), "Update last run date");
		}
		catch (Exception e) {
			String wxMsg = "?????????"+processDt+"\n???????????????????????????\n?????????????????????config.dat??????????????????????????????";
			System.out.println(wxMsg+"\n"+e);
			msg.execute(wxMsg);
			System.exit(255);
		}
	}
	
}
