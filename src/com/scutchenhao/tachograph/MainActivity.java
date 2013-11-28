package com.scutchenhao.tachograph;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.view.Menu;
import java.io.IOException; 
import android.graphics.PixelFormat; 
import android.media.MediaRecorder; 
import android.view.SurfaceHolder; 
import android.view.SurfaceView; 
import android.view.View; 
import android.view.View.OnClickListener; 
import android.widget.Button; 

public class MainActivity extends Activity implements SurfaceHolder.Callback {
	private Button start;// ��ʼ¼�ư�ť 
    private Button stop;// ֹͣ¼�ư�ť 
    private MediaRecorder mediarecorder;// ¼����Ƶ���� 
    private SurfaceView surfaceview;// ��ʾ��Ƶ�Ŀؼ� 
    // ������ʾ��Ƶ��һ���ӿڣ��ҿ����û����У�Ҳ����˵��mediaRecorder¼����Ƶ���ø������濴 
    private SurfaceHolder surfaceHolder;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        // ѡ��֧�ְ�͸��ģʽ,����surfaceview��activity��ʹ�á� 
        getWindow().setFormat(PixelFormat.TRANSLUCENT); 
        setContentView(R.layout.activity_main); 

        init(); 
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		// ��holder�����holderΪ��ʼ��oncreat����ȡ�õ�holder����������surfaceHolder 
        surfaceHolder = arg0; 
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// ��holder�����holderΪ��ʼ��onCreat����ȡ�õ�holder����������surfaceHolder 	
        surfaceHolder = holder; 
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// surfaceDestroyed��ʱ��ͬʱ��������Ϊnull 
        surfaceview = null; 
        surfaceHolder = null; 
        mediarecorder = null; 
	}
	
	@SuppressWarnings("deprecation")
	private void init() { 
        start = (Button) this.findViewById(R.id.start); 
        stop = (Button) this.findViewById(R.id.stop); 
        start.setOnClickListener(new TestVideoListener()); 
        stop.setOnClickListener(new TestVideoListener()); 
        surfaceview = (SurfaceView) this.findViewById(R.id.surfaceview); 
        SurfaceHolder holder = surfaceview.getHolder();// ȡ��holder 
        holder.addCallback(this); // holder����ص��ӿ� 
        //setType�������ã�Ҫ������. 
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); 

    } 
	
	class TestVideoListener implements OnClickListener { 
        @Override 
        public void onClick(View v) { 
            if (v == start) { 
                mediarecorder = new MediaRecorder();// ����mediarecorder���� 
                // ����¼����ƵԴΪCamera(���) 
                mediarecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA); 
                // ����¼����ɺ���Ƶ�ķ�װ��ʽTHREE_GPPΪ3gp.MPEG_4Ϊmp4 
                mediarecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP); 
                // ����¼�Ƶ���Ƶ����h263 h264 
                mediarecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); 
                // ������Ƶ¼�Ƶķֱ��ʡ�����������ñ���͸�ʽ�ĺ��棬���򱨴� 
                mediarecorder.setVideoSize(176, 144); 
                // ����¼�Ƶ���Ƶ֡�ʡ�����������ñ���͸�ʽ�ĺ��棬���򱨴� 
                mediarecorder.setVideoFrameRate(20); 
                mediarecorder.setPreviewDisplay(surfaceHolder.getSurface()); 
                // ������Ƶ�ļ������·�� 
                mediarecorder.setOutputFile(Environment.getExternalStorageDirectory().getPath() + "/love.3gp"); 

                try { 
                    mediarecorder.prepare(); 
                    mediarecorder.start(); 

                } catch (IllegalStateException e) { 
                    e.printStackTrace(); 

                } catch (IOException e) { 
                    e.printStackTrace(); 
                } 
            } 
            if (v == stop) { 
                if (mediarecorder != null) { 
                    // ֹͣ¼�� 
                    mediarecorder.stop(); 
                    // �ͷ���Դ 
                    mediarecorder.release(); 
                    mediarecorder = null; 
                } 
            } 
        } 

    } 
}
