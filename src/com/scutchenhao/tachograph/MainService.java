package com.scutchenhao.tachograph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

public class MainService extends Service {
	public static final String TAG = "ScutTachograph:Service";
	public static final boolean DEBUG = MainActivity.DEBUG;
	public static final String URL = "http://datatransfer.duapp.com/hello";
	public static final String FILEDIR = StorageManager.LOG_PATH;
	public static final int SEND_DELTA_TIME = 2000;
	private File gpsFile;
	private FileOutputStream gpsFileStream;
    public boolean sendFlag = true;
    public boolean receiveFlag = true;
    public boolean networkAvailableFlag = false;
    private boolean gpsFlag = false;
    private double latitude = 0;
    private double longitude = 0;
    private double altitude = 0;
    private int firstLocated = 0;
    private long firstLocatedTime = 0;
    private int fire = 0;
    private String log = "";
    private List<MyGpsLocation> locationList = new ArrayList<MyGpsLocation>();
    private final IBinder mBinder = new LocalBinder();
    private boolean hasGpsData = false;
    private boolean isServiceConnected = false;

    public class LocalBinder extends Binder {
        MainService getService() {
        	isServiceConnected = true;
            return MainService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
    	return mBinder;
    }

	@Override
	public void onCreate() {
		super.onCreate();
    	sendLog("����������");
    	
        initSdcard();
        	

        initNetwork();

        initLocation();

		//Զ�̽�������
        new ReceiveThread().start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		sendFlag = false;
		try {
			if (!hasGpsData) {
				gpsFileStream.close();
				gpsFile.delete();
			} else {
				sendLog("�������ļ�");
			}
		} catch (IOException e) {
			sendLog("�ļ��ر�ʧ��");
		}
		sendLog("�����˳�");
    	gpsFlag = false;
    	locationManager.removeGpsStatusListener(gpsStatusListener);
    	locationManager.removeUpdates(gpsListener);
    	locationManager.removeUpdates(gprsListener);
    	
	}
	
    private void sendLog(String msg){
    	if (DEBUG)
    		Log.v(TAG, msg);
    	log = log.concat(msg + '\n');
    	if (!isServiceConnected)		//avoid repeat log in log textview due to the delay time of boardcast
    		return;
    	Intent mIntent = new Intent(UpdateReceiver.MSG);
    	mIntent.putExtra(UpdateReceiver.DATA_TYPE, UpdateReceiver.LOG_DATA);
    	mIntent.putExtra(UpdateReceiver.DATA, msg);
        this.sendBroadcast(mIntent);
    }


    private void sendGPS(Location location){
    	Intent mIntent = new Intent(UpdateReceiver.MSG);
    	mIntent.putExtra(UpdateReceiver.DATA_TYPE, UpdateReceiver.GPS_DATA);
    	mIntent.putExtra(UpdateReceiver.DATA, location);
        this.sendBroadcast(mIntent);
    }

    /**
     * SD card
     */
    private void initSdcard() {
        //���������ļ�
        if (!hasSdcard()) {
        	sendLog("δ�ҵ�sd����������������");
		} else {
			File dir = new File(FILEDIR);
				if (!dir.exists()) {
					if (dir.mkdir())
						sendLog("����Ŀ¼" + FILEDIR);
				}
	        try {
	            String fileName = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.CHINESE).format(new Date()) + ".txt";
	        	gpsFile = new File(FILEDIR + fileName);
	        	if (!gpsFile.exists()){
	                 try {
	                	 gpsFile.createNewFile();
						sendLog("������¼�ļ�" + gpsFile.toString());
					} catch (IOException e) {
						sendLog("�����ļ�ʧ��");
						return;
					}
	            }
	        	gpsFileStream = new FileOutputStream(gpsFile);
			} catch (FileNotFoundException e) {
				sendLog("�����ļ�ʧ��");
			}

        }

    }

    public static boolean hasSdcard() {
        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_MOUNTED)) {
            return true;
        } else {
            return false;
        }
    }

    public class SendLoopThread extends Thread {
	    @Override
	    public void run() {
	    	long deltaTime = System.currentTimeMillis();
	        while(sendFlag){
				if (System.currentTimeMillis() - deltaTime >= SEND_DELTA_TIME) {
					deltaTime = System.currentTimeMillis();
					new SendThread(":" + latitude + ":" + longitude).start();
				}
	        }
	    }
    }

    /**
     * Network
     */
    private void initNetwork() {
    	if (isConnect(this)) {
    		networkAvailableFlag = true;
    		sendLog("��������");
    	} else {
    		networkAvailableFlag = false;
    		sendLog("����δ���ӣ��޷����ͽ�������");
    	}
    }

    public class ReceiveThread extends Thread {
    	private long time = 0;
	    @Override
	    public void run() {
	    	while(receiveFlag && networkAvailableFlag) {
	    		long newTime = System.currentTimeMillis();
	    		if (newTime - time < 300)
	    			continue;
	    		time = newTime;
	    		
	    		String data = getData();

	    		if (data.contains("n"))
	    			return;
	    		
	    		int i = data.indexOf(':');
	    		int j = data.indexOf(':', data.indexOf(':') + 1);
	    		latitude = Double.parseDouble(data.substring(i + 1, j - 1));
	    		longitude = Double.parseDouble(data.substring(j + 1));
	    		Location location = new Location(LocationManager.GPS_PROVIDER);
	    		location.setLatitude(latitude);
	    		location.setLongitude(longitude);
	    		sendGPS(location);
	    	}
	    }
    }

    public class SendThread extends Thread {
    	String data;
    	public SendThread(String data) {
    		this.data = data;
    	}
    	
	    @Override
	    public void run() {
	    	if (networkAvailableFlag) {
	    		String result = sendData(data);
	    		
	    		if (!result.equals("ok"))
	    			sendLog("���ݷ���ʧ�ܣ�" + result);
	    	}
	    }
    }

    private boolean isConnect(Context context) {
        // ��ȡ�ֻ��������ӹ�����󣨰�����wi-fi,net�����ӵĹ���
	    try {
	        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	        if (connectivity != null) {
	            // ��ȡ�������ӹ���Ķ���
	            NetworkInfo info = connectivity.getActiveNetworkInfo();
	            if (info != null&& info.isConnected()) {
	                // �жϵ�ǰ�����Ƿ��Ѿ�����
	                if (info.getState() == NetworkInfo.State.CONNECTED) {
	                    return true;
	                }
	            }
	        }
	    } catch (Exception e) {
	    	sendLog("��ȡ����״̬����");
	    }
        return false;
    }

    /**
     * GPS
     */
    private boolean recordFlag = false;
    private LocationManager locationManager;
    private LocationListener gpsListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
	        updateToNewLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			if (firstLocated != 0)
				sendLog("GPS�ر�");
		}

		@Override
		public void onProviderEnabled(String provider) {
			sendLog("GPS��");
		}

		@Override
		public void onStatusChanged(String provider, int status,
				Bundle extras) {
		}
    	
    };
    private LocationListener gprsListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
	        updateToNewLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			if (firstLocated != 0)
				sendLog("GPS�ر�");
		}

		@Override
		public void onProviderEnabled(String provider) {
			sendLog("GPS��");
		}

		@Override
		public void onStatusChanged(String provider, int status,
				Bundle extras) {
		}
    	
    };

    private GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
    	private int count = -1;
		@Override
		public void onGpsStatusChanged(int event) {
            switch (event) {
            //��һ�ζ�λ
            case GpsStatus.GPS_EVENT_FIRST_FIX:
            	sendLog("��һ�ζ�λ");
                break;
            //����״̬�ı�
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
//                sendLog("����״̬�ı�");
                //��ȡ��ǰ״̬
                GpsStatus gpsStatus=locationManager.getGpsStatus(null);
                //��ȡ���ǿ�����Ĭ�����ֵ
                int maxSatellites = gpsStatus.getMaxSatellites();
                //����һ��������������������
                Iterator<GpsSatellite> iters = gpsStatus.getSatellites().iterator();
                int newCount = 0;
                while (iters.hasNext() && newCount <= maxSatellites) {
                	newCount++;
                }
                if (count != newCount) {
                	sendLog("��������" + newCount + "������");
                	count = newCount;
                }

                break;
            //��λ����
            case GpsStatus.GPS_EVENT_STARTED:
            	sendLog("��λ����");
                //�õ������Location
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null)
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location == null)
                	break;
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                altitude = location.getAltitude();
                break;
            //��λ����
            case GpsStatus.GPS_EVENT_STOPPED:
            	sendLog("��λ����");
                break;
            }
		}
    };

    private void initLocation() {
    	gpsFlag = true;
	    locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	    if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            sendLog("GPSģ������");
        } else {
        	sendLog("GPSģ��رգ����ֶ���");
        }

        // ���ü��������Զ����µ���Сʱ��Ϊ���N��(1��Ϊ1*1000������д��ҪΪ�˷���)����Сλ�Ʊ仯����N��
        locationManager.addGpsStatusListener(gpsStatusListener);
        List<String> list = locationManager.getAllProviders();

		//gprs��λ
        if (list.contains(LocationManager.NETWORK_PROVIDER))
        	locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SEND_DELTA_TIME, 0, gprsListener);
		//GPS ��λ
        if (list.contains(LocationManager.GPS_PROVIDER))
        	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, SEND_DELTA_TIME, 0, gpsListener);


        Thread gpsFileThread = new Thread() {
			@Override
			public void run() {
				while(gpsFlag) {
					try {
						sleep(SEND_DELTA_TIME);
					} catch (InterruptedException e1) {
						sendLog("GPSд�̳߳���");
					}
					if (recordFlag) {
						Date now = new Date();
			            String time = DateFormat.getDateTimeInstance().format(now);
			            String gpsData = "GPS: " + latitude + "," + longitude + "\r\n";
			        	try {
			        		if (gpsFileStream != null)
			        			gpsFileStream.write((time + '\t' + gpsData).getBytes());
			        			hasGpsData = true;
						} catch (IOException e) {
							sendLog("д��GPS����ʧ��");
						}
		        	}
				}
			}
        };
        gpsFileThread.start();
    }
	
    private void updateToNewLocation(Location location) {
        if (location != null) {
        	latitude = location.getLatitude();
            longitude = location.getLongitude();
            altitude = location.getAltitude();
            if (firstLocated == 1) {
                firstLocatedTime = System.currentTimeMillis();
            	sendLog("�״ζ�λ�ɹ���ά�ȣ�" +  latitude + "�����ȣ�" + longitude);
            	locationManager.removeUpdates(gprsListener);
        		locationList.clear();
            	firstLocated++;
                new SendLoopThread().start();
                recordFlag = true;
            } else {
            	firstLocated++;
            }
            sendGPS(location);

            if (firstLocated > 1) {
            	if(altitude == 0)		//��վ��λ���ݣ�����׼ȷ�����Ե�
            		return;
            		
            	long time = System.currentTimeMillis() - firstLocatedTime;
	            locationList.add(new MyGpsLocation(latitude, longitude, time, fire));
            }

        } else {
        	sendLog("�޷���ȡGPS��Ϣ");
        }
    }

    /**
     * Internet
     */
	private String getData() {
		SharedPreferences settings = getSharedPreferences("setting", MODE_PRIVATE);
		String id = settings.getString("id", MainActivity.DEFAULT_ID);
		try {
			HttpURLConnection connection;
			URL server;
			server = new URL(URL + "?id=" + id);
			connection = (HttpURLConnection)server.openConnection();
			connection.setReadTimeout(10 * 1000);
			connection.setRequestMethod("GET");
			InputStream inStream = connection.getInputStream();
			ByteArrayOutputStream data = new ByteArrayOutputStream();		//�½�һ�ֽ����������
			byte[] buffer = new byte[1024];		//���ڴ��п���һ�λ���������������������
			int len=0;
			while((len = inStream.read(buffer)) != -1) {
				data.write(buffer, 0, len);		//����������֮�󽫻�����������д�������
			}
			inStream.close();
			return new String(data.toByteArray(),"utf-8");		//�����Խ��õ��������ת��utf-8������ַ�������ɽ�һ������
		} catch (MalformedURLException e) {
			sendLog("URL����");
			return "null";
		} catch (IOException e) {
			sendLog("Զ�����ݻ�ȡʧ��");
			return "null";
		}
	}

	private String sendData(String content) {
		SharedPreferences settings = getSharedPreferences("setting", MODE_PRIVATE);
		String id = settings.getString("id", MainActivity.DEFAULT_ID);
		try {
			HttpURLConnection connection;
			URL server;
			server = new URL(URL + "?id=" + id + "&data=" + content);
			connection = (HttpURLConnection)server.openConnection();
			connection.setReadTimeout(10 * 1000);
			connection.setRequestMethod("GET");
			InputStream inStream = connection.getInputStream();
			ByteArrayOutputStream data = new ByteArrayOutputStream();//�½�һ�ֽ����������
			byte[] buffer = new byte[1024];//���ڴ��п���һ�λ���������������������
			int len=0;
			while((len=inStream.read(buffer))!=-1){
				data.write(buffer, 0, len);//����������֮�󽫻�����������д�������
			}
			inStream.close();
			return new String(data.toByteArray(),"utf-8");//�����Խ��õ��������ת��utf-8������ַ�������ɽ�һ������
		} catch (MalformedURLException e) {
			sendLog("URL����");
			return "";
		} catch (IOException e) {
			sendLog("Զ�����ݻ�ȡʧ��");
			return "";
		}
	}

    /**
     * Draw Location
     */
	public class MyGpsLocation {
		public double latitude;
    	public double longitude;
    	public long time;
    	
    	MyGpsLocation(double latitude, double longitude, long time, int fire) {
    		this.latitude = latitude;
    		this.longitude = longitude;
    		this.time = time;
    	}   	
    }
	
    /**
     * Activity����
     */
	protected double getLatitude() {
		return latitude;
	}
	
	protected double getLongitude() {
		return longitude;
	}
	
    protected String getLog() {
    	return log;
    }

}