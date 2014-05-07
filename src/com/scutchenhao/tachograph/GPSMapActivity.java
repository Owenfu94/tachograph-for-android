package com.scutchenhao.tachograph;

import com.scutchenhao.tachograph.MainService.LocalBinder;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector.OnGestureListener;
import android.widget.Toast;

public class GPSMapActivity extends Activity {
	private final static String TAG = "ScutTachograph:GpsMapActivity";
	private boolean firstTime = true;
	private boolean showLocationFlag = false;
	private long backTime = 0;
	private GoogleMap map;
	private Marker marker;
	private Location location = new Location(LocationManager.GPS_PROVIDER);
    private UpdateReceiver receiver = new UpdateReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	    	String dataType = intent.getStringExtra(DATA_TYPE);
	    	if (dataType.equals(GPS_DATA)) {
	    		if(showLocationFlag)
	    			return;
	    		
		    	location = (Location)intent.getParcelableExtra(DATA);
		    	LatLng newLatLng = new LatLng(location.getLatitude(), location.getLongitude());
		    	log("λ�ã�" + location.getLatitude() + "��" + location.getLongitude());
		    	log("������룺" + ((marker == null)?-1 : calculateDistance(newLatLng, marker.getPosition())));
		        if (firstTime) {
			        marker = map.addMarker(new MarkerOptions()
				        .position(newLatLng)
				        .title("�ҵ�λ��"));
			        map.moveCamera(CameraUpdateFactory.zoomTo(15));
		        	map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
		        	firstTime = false;
		        } else if (calculateDistance(newLatLng, marker.getPosition()) >= 0.0005) {
			    	marker.remove();
	        		map.addPolyline(new PolylineOptions()
	        				.add(marker.getPosition() , newLatLng)
	        				.width(10)
	        				.color(Color.YELLOW));
			        marker = map.addMarker(new MarkerOptions()
				        .position(newLatLng)
				        .title("�ҵ�λ��"));
			    	log("���µ�");
		    	}
	    	}
	    }
    };

	//RefreshService����
    @SuppressWarnings("unused")
	private MainService mService;
    private LocalBinder serviceBinder;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
        		serviceBinder = (LocalBinder)service;
                mService = serviceBinder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        //����ʶ��
		mGestureDetector = new GestureDetector(this, mGestureListener, null);
        mGestureDetector.setIsLongpressEnabled(true);

    }

	@Override
	protected void onStart() {
		super.onStart();

		//�󶨲�����Service
        Intent intent = new Intent();
	    intent.setClass(this, MainService.class);
	    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	
    	//����Service�㲥��Ϣ
        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateReceiver.MSG);
        this.registerReceiver(receiver, filter);
		
	}

	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(receiver);
		unbindService(mConnection);
	}

	private static double calculateDistance(LatLng l1, LatLng l2) {
		/*
		 * γ��1�� = ��Լ111km
		 * γ��1�� = ��Լ1.85km
		 * γ��1�� = ��Լ30.9m 
		 * 200m Լ���� 0.002
		 * 50m Լ���� 0.0005
		*/
		double delLat = l1.latitude - l2.latitude;
		double delLng = l1.longitude - l2.longitude;
		double distance = Math.pow(Math.pow(delLat, 2) + Math.pow(delLng, 2), 0.5);
		return distance;
	}
	//�������¼�����mGestureDetector���������޷�ʶ��
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		mGestureDetector.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}
	
	//����ʶ��
	private GestureDetector mGestureDetector;
	private OnGestureListener mGestureListener = new OnGestureListener() {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			if (velocityX < -1500)
				newActivity(MainActivity.TURN_LEFT);
			else if (velocityX > 1500)
				newActivity(MainActivity.TURN_RIGHT);
			return false;
		}

		@Override
		public void onLongPress(MotionEvent e) {
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
				float distanceY) {
			return false;
		}

		@Override
		public void onShowPress(MotionEvent e) {
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}
	};
	
	private void newActivity(int dir) {
		Intent intent = new Intent();
		intent.setClass(GPSMapActivity.this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);

		if (dir == MainActivity.TURN_LEFT)		//�����л����������ұ߽��룬����˳�
			overridePendingTransition(R.animator.in_from_right, R.animator.out_to_left);
		if (dir == MainActivity.TURN_RIGHT)	//�����л�����������߽߱��룬�ұ߱��˳�
			overridePendingTransition(R.animator.in_from_left, R.animator.out_to_right);		
		
		finish();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			long newTime = System.currentTimeMillis();
			if (newTime - backTime <= 5000) {
				return super.onKeyDown(keyCode, event);
			} else {
				backTime = newTime;
				Toast.makeText(this, "�ٰ�һ���˳�", Toast.LENGTH_SHORT).show();
				return true;
			}
        }

		return true;
	}
	
	private void log(String log) {
		Log.v(TAG, log + "\n");
	}
} 