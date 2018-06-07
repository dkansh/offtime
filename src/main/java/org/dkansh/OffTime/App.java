package org.dkansh.OffTime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author A-7782
 *
 */
public class App {
	private static List<String> cookies;
	private static HttpURLConnection conn;
	private final static String USER_AGENT = "Mozilla/5.0";
	private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private static DateFormat showDateFormat = new SimpleDateFormat("dd-MMM-yyy");
	private static Date lowDate = new Date();
	private static Date highDate = new Date();
	private static List<String> timeList = new ArrayList<String>();

	public static void main(String[] args) throws Exception {
		CookieHandler.setDefault(new CookieManager());

		getAffectedDateList();
		String employeeId = getEmployeeId();

		String url = "http://192.168.7.31:5001/api/AttendanceAPI/?employeeId=" + employeeId + "&start="
				+ dateFormat.format(lowDate) + "&end=" + dateFormat.format(highDate);

		String page = getPageContent(url);
		parseHtml(page);
		System.in.read();
	}

	private static String getEmployeeId() throws Exception {
		String body = getPageContent("http://192.168.7.31:5001/api/EmployeeAPI/0");
		Document doc = Jsoup.parse(body);
		Element element = doc.getElementsByTag("string").first();
		System.out.println("Employee ID : " + element.text() + "\n");
		return element.text();
	}

	private static void parseHtml(String html) throws ParseException {
		Document doc = Jsoup.parse(html);
		Elements elements = doc.getElementsByTag("CalendarEvent");
		boolean flag = true;
		for (Element element : elements) {
			Element startTag = element.getElementsByTag("start").first();
			String date = startTag.text();
			Date startDate = dateFormat.parse(date);
			if (startDate.getTime() >= lowDate.getTime() && startDate.getTime() <= highDate.getTime()) {
				Element titleTag = element.getElementsByTag("title").first();
				String temp = titleTag.text();
				String time = "";
				if (temp.substring(temp.indexOf('>') + 1, temp.lastIndexOf('<')).contains("Present")) {
					temp = temp.substring(temp.lastIndexOf("title"), temp.lastIndexOf("'>"));
					if (date.equalsIgnoreCase(dateFormat.format(new Date()))) {
						time = temp.substring(temp.indexOf('\'') + 1);
						DateFormat tempDateFormat = new SimpleDateFormat("HH:mm");
						Date date1 = tempDateFormat.parse(time);
						Date date2 = tempDateFormat.parse(tempDateFormat.format(new Date()));
						long milSec = date2.getTime() - date1.getTime();
						time = "" + milSec / (60 * 60 * 1000) % 24 + ":" + milSec / (60 * 1000) % 60;
						timeList.add(time);
						System.out.println(showDateFormat.format(startDate) + " : " + time);
						flag = false;
					} else {
						time = temp.substring(temp.lastIndexOf('(') + 1, temp.lastIndexOf(')'));
						System.out.println(showDateFormat.format(startDate) + " : " + time);
						timeList.add(time);
					}
				} else if (temp.substring(temp.indexOf('>') + 1, temp.lastIndexOf('<')).contains("Full Day Leave")) {
					System.out.println(showDateFormat.format(startDate) + " : Full Day Leave");
				} else {
					System.out.println(showDateFormat.format(startDate) + " : Absent");
				}
			}
		}
		int sumHours = 0;
		int sumMin = 0;
		for (String time : timeList) {
			String[] temp = time.split(":");
			int hours = Integer.parseInt(temp[0]);
			int minutes = Integer.parseInt(temp[1]);
			sumHours += hours;
			sumMin += minutes;
		}
		sumHours += sumMin / 60;
		sumMin %= 60;
		int expected = Integer.valueOf(timeList.size() * 9);

		System.out.println("\n\nExpected Hours\t : " + expected + " Hrs");
		System.out.println("Actual Hours\t : " + sumHours + " Hrs " + sumMin + " Min");

		if (expected > sumHours) {
			int differenceMinutes = expected * 60 - (sumHours * 60 + sumMin);
			int hours = differenceMinutes / 60;
			int min = differenceMinutes % 60;
			System.out.println("\nLacking by " + hours + " hours and " + min + " minutes");
		} else {
			int differenceMinutes = sumHours * 60 + sumMin - expected * 60;

			int hours = differenceMinutes / 60;
			int min = differenceMinutes % 60;

			System.out.println("\nGaining by " + hours + " hours and " + min + " minutes");
		}
		
		if(flag){
			System.out.println("\nMissing todays entry!!!");
		}
	}

	private static String getPageContent(String url) throws Exception {
		URL obj = new URL(url);
		conn = (HttpURLConnection) obj.openConnection();
		conn.setRequestMethod("GET");
		conn.setUseCaches(false);

		conn.setRequestProperty("User-Agent", USER_AGENT);
		conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
		if (cookies != null) {
			for (String cookie : cookies) {
				conn.addRequestProperty("Cookie", cookie.split(";", 1)[0]);
			}
		}
		// int responseCode = conn.getResponseCode();
		// System.out.println("\nSending 'GET' request to URL : " + url);
		// System.out.println("Response Code : " + responseCode);

		BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		cookies = conn.getHeaderFields().get("Set-Cookie");

		return response.toString();

	}

	private static List<String> getAffectedDateList() throws Exception {
		List<String> datesToconsider = new ArrayList<String>();
		Calendar cal = Calendar.getInstance();
		cal.setTime(dateFormat.parse(dateFormat.format(new Date())));
		int day = cal.get(7);
		cal.add(5, -day + 2);
		for (int k = 2; k <= day; k++) {
			int date = cal.get(5);
			datesToconsider.add(Integer.toString(date));
			Date d = populateDate(date, cal);
			if (d.before(lowDate)) {
				lowDate = d;
			}
			if (d.after(highDate)) {
				highDate = d;
			}
			cal.add(5, 1);
		}
		return datesToconsider;
	}

	private static Date populateDate(int date, Calendar cal) {
		cal.set(5, date);
		return cal.getTime();
	}
}
