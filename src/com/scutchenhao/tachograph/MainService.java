package com.scutchenhao.tachograph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
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
    public boolean bluetoothFlag = true;
    public boolean receiveFlag = true;
    public boolean networkAvailableFlag = false;
    private boolean gpsFlag = false;
    private double latitude = 0;
    private double longitude = 0;
    private double altitude = 0;
    private int firstLocated = 0;
    private String log = "";
//    private boolean aRound = false;
    //�������ڷ���UUID
//    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
	// ʵ�����Զ����Binder��  
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
        
	}
    
	@Override
	public void onDestroy() {
		super.onDestroy();
		bluetoothFlag = false;
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
		sendLog("�����˳�");
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
            	sendLog("�״ζ�λ�ɹ���ά�ȣ�" +  latitude + "�����ȣ�" + longitude + "�����Σ�" + altitude);
        		Toast.makeText(MainService.this, "�״ζ�λ�ɹ���ά�ȣ�" +  latitude + "�����ȣ�" + longitude + "�����Σ�" + altitude, Toast.LENGTH_SHORT).show();
            	locationManager.removeUpdates(gprsListener);
            	firstLocated++;
            } else {
            	firstLocated++;
            }
            sendGPS(location);
            
            if (firstLocated > 1) {
            	if(altitude == 0)		//gprs��λ���ݣ�����׼ȷ�����Ե�
            		return;
	            
            }
            
        } else {
        	sendLog("�޷���ȡGPS��Ϣ");
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
	
    protected boolean getRecordFlag() {
    	return recordFlag;
    }

    protected String getLog() {
    	return log;
    }    
    
    
}