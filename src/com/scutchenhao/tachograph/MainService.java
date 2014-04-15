package com.scutchenhao.tachograph;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
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
import android.widget.Toast;

public class MainService extends Service {
	public static final String ID = "3";
	public static final String FILEDIR = Environment.getExternalStorageDirectory().getPath() + "/SerialPortData/";
	private FileOutputStream dataFile;
    private BluetoothAdapter mBluetoothAdapter = null;
    public boolean sendFlag = true;
    public boolean receiveFlag = true;
    public boolean networkAvailableFlag = false;
    private BluetoothSocket btSocket = null;
    private boolean gpsFlag = false;
    private double latitude = 0;
    private double longitude = 0;
    private double altitude = 0;
    private int firstLocated = 0;
    private long firstLocatedTime = 0;
    private int fire = 0;
    private List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
    private String log = "";
    private List<MyGpsLocation> locationList = new ArrayList<MyGpsLocation>();
    private final IBinder mBinder = new LocalBinder();  
    
    public class LocalBinder extends Binder {  
        MainService getService() {  
            // ����Activity��������Service����������Activity��Ϳɵ���Service���һЩ���÷����͹�������  
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
			if (dataFile != null) {
				sendLog("�ر��ļ�");
				dataFile.close();
			} else {
				sendLog("�������ļ�");
			}
		} catch (IOException e) {
			sendLog("�ļ��ر�ʧ��");
		}
		sendLog("�����˳����ر�����");
    	if (mBluetoothAdapter != null) {
    		while(mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON);
    		mBluetoothAdapter.disable();
    	}
    	gpsFlag = false;
    	locationManager.removeGpsStatusListener(gpsStatusListener);
    	locationManager.removeUpdates(gpsListener);
    	locationManager.removeUpdates(gprsListener);
    	
	}
	
    private void sendLog(String msg){  
    	log = log.concat(msg + '\n');
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
	        Calendar c = Calendar.getInstance();
	        int year = c.get(Calendar.YEAR); 
	        int month = c.get(Calendar.MONTH) + 1; 
	        int date = c.get(Calendar.DATE); 
	        int hour = c.get(Calendar.HOUR_OF_DAY); 
	        int minute = c.get(Calendar.MINUTE); 
	        int second = c.get(Calendar.SECOND);  
	        try {
	        	File file = new File(FILEDIR + year + "-" + month + "-" + date + " " +hour + "-" +minute + "-" + second + ".txt");
	        	if (!file.exists()){    
	                 try {
						file.createNewFile();
						sendLog("������¼�ļ�" + file.toString());
					} catch (IOException e) {
						sendLog("�����ļ�ʧ��");
						return;
					}    
	            }    
	        	dataFile = new FileOutputStream(file);
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
    
    public class RecordThread extends Thread {
	    @Override
	    public void run() {
	    	long deltaTime = System.currentTimeMillis();
	        while(sendFlag){
				if (System.currentTimeMillis() - deltaTime >= 500) {
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
        	locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 0, gprsListener);
		//GPS ��λ
        if (list.contains(LocationManager.GPS_PROVIDER))
        	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, gpsListener);

        
        Thread gpsThread = new Thread() {
			@Override
			public void run() {
				while(gpsFlag) {
					try {
						sleep(500);
					} catch (InterruptedException e1) {
						sendLog("GPSд�̳߳���");
					}
					if (recordFlag) {
						Date now = new Date();
			            String time = DateFormat.getDateTimeInstance().format(now);
			            String gpsData = "GPS: " + latitude + "," + longitude + "," + altitude + "\r\n";
			        	try {
			        		if (dataFile != null)
			        			dataFile.write((time + '\t' + gpsData).getBytes());
						} catch (IOException e) {
							sendLog("д��GPS����ʧ��");
						}
		        	}
				}
			}
        };
        gpsThread.start();
    }
	
    private void updateToNewLocation(Location location) {
        if (location != null) {
        	latitude = location.getLatitude();
            longitude = location.getLongitude();
            altitude = location.getAltitude();
            if (firstLocated == 1) {
                firstLocatedTime = System.currentTimeMillis();
            	sendLog("�״ζ�λ�ɹ���ά�ȣ�" +  latitude + "�����ȣ�" + longitude + "�����Σ�" + altitude);
        		Toast.makeText(MainService.this, "�״ζ�λ�ɹ���ά�ȣ�" +  latitude + "�����ȣ�" + longitude + "�����Σ�" + altitude, Toast.LENGTH_SHORT).show();
            	locationManager.removeUpdates(gprsListener);
        		locationList.clear();
            	firstLocated++;
            } else {
            	firstLocated++;
            }
            sendGPS(location);
            
            if (firstLocated > 1) {
            	if(altitude == 0)		//gprs��λ���ݣ�����׼ȷ�����Ե�
            		return;
            		
            	long time = System.currentTimeMillis() - firstLocatedTime;
	            locationList.add(new MyGpsLocation(latitude, longitude, time, fire));
	            
	        /*
	            MyGpsLocation firstLoction = locationList.get(0);
	            double distance = Math.abs(latitude - firstLoction.latitude) + Math.abs(longitude - firstLoction.longitude);
	            if (locationList.size() >= 100 && distance <= 0.001) {		//0.00027 �� 30m
	            	Toast.makeText(this, "���һȦ", Toast.LENGTH_SHORT).show();
	            	aRound = true;
	            }
	            if (aRound && distance >= 0.001) {
	            	Toast.makeText(this, "���¿�ʼ��¼", Toast.LENGTH_SHORT).show();
	            	locationList.clear();
	            	aRound = false;
	            }
	        */
            }
            
        } else {
        	sendLog("�޷���ȡGPS��Ϣ");
        }
    }
    
    /**
     * Internet
     */
	protected String getData() {
		try {
			HttpURLConnection connection;
			URL server;
			server = new URL("http://datatransfer.duapp.com/hello?id=" + ID);
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

	protected String sendData(String content) {
		try {
			HttpURLConnection connection;
			URL server;
			server = new URL("http://datatransfer.duapp.com/hello?id=" + ID + "&data=" + content);
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
    	public int fire;
    	
    	MyGpsLocation(double latitude, double longitude, long time, int fire) {
    		this.latitude = latitude;
    		this.longitude = longitude;
    		this.time = time;
    		this.fire = fire;
    	}   	
    }
	
	public void saveLocation() {
		if(!hasSdcard()) {
			Toast.makeText(this, "δ�ҵ�sdcard������ʧ��", Toast.LENGTH_SHORT).show();
			return;
		}
        
		try {
        	File file = new File(FILEDIR + "location_list.txt");
        	if (!file.exists()){    
                 try {
					file.createNewFile();
					sendLog("����GPS�ļ�" + file.toString());
				} catch (IOException e) {
					sendLog("�����ļ�ʧ��");
					return;
				}    
            } else {
            	if(!file.delete()) {
					sendLog("ɾ��ԭ���ļ�ʧ��");
					return;
            	} else {
            		try {
						file.createNewFile();
						sendLog("����GPS�ļ�" + file.toString());
						
					} catch (IOException e) {
						sendLog("�����ļ�ʧ��");
						return;
					}
            	}
            }
        	FileOutputStream locationData = new FileOutputStream(file);
    		try {
    			for(MyGpsLocation i: locationList) {
					locationData.write((i.time + "," + i.latitude + "," + i.longitude + "," + i.fire + "\n").getBytes());

				}
    			locationData.close();
        	} catch (IOException e) {
				sendLog("д��λ������ʧ��");
				return;
        	}
        	
		} catch (FileNotFoundException e) {
			sendLog("�����ļ�ʧ��");
			return;
		}
		
		Toast.makeText(this, "����ɹ���������" + locationList.size() + "��GPS��Ϣ", Toast.LENGTH_SHORT).show();
	}
	
	public List<MyGpsLocation> loadLocation() {
        try {
        	File file = new File(FILEDIR + "location_list.txt");
        	List<MyGpsLocation> drawList = new ArrayList<MyGpsLocation>();
        	if (!file.exists()){    
				sendLog("��λ�������ļ�");   
            } else {
            	 BufferedReader locationData = new BufferedReader(new FileReader(file));
            	 try {
            		while (true) {
            			String str = locationData.readLine();
            			if (str == null)
            				break;
            			
            			 int i = str.indexOf(',');
            			 int j = str.indexOf(',', i + 1);
            			 int k = str.indexOf(',', j + 1);
            			 long time = Long.parseLong(str.substring(0, i));
            			 double latitude = Double.parseDouble(str.substring(i + 1, j));
            			 double longitude = Double.parseDouble(str.substring(j + 1, k));
            			 int fire = Integer.parseInt(str.substring(k + 1));
            			 drawList.add(new MyGpsLocation(latitude, longitude, time, fire));
            		}
            		locationData.close();
				} catch (IOException e) {
					sendLog("λ�����ݸ�ʽ����");
					return null;
				}
            }
            return drawList;
        } catch (FileNotFoundException e) {
			sendLog("�����ļ�ʧ��");
			return null;
		}
        
	}
	
    /**
     * Activity����
     */
	protected double getAltitude() {
		return altitude;
	}

	protected double getLatitude() {
		return latitude;
	}
	
	protected double getLongitude() {
		return longitude;
	}
	
    protected List<MyGpsLocation> getLocationList() {
    	return loadLocation();
    }

    protected void setRecordFlag(boolean valve) {
    	recordFlag = valve;
    }
    
    protected void refresh() {
		deviceList.clear();
        try {
        	if (btSocket != null) {
            	sendLog("���ڹر�ԭ���ӡ���");
        		btSocket.close();
        	}
            sendFlag = false;		//�ر��������߳�
        } catch (IOException e2) {
        	sendLog("�׽��ֹر�ʧ��");
        }
        
    	mBluetoothAdapter.cancelDiscovery();
    	mBluetoothAdapter.startDiscovery();
    	sendLog("ˢ���豸�б�......");
    }
    
    protected String getLog() {
    	return log;
    }    
    
}