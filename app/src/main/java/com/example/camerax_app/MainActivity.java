package com.example.camerax_app;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.core.impl.CameraCaptureCallback;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.ImageAnalysisConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.util.concurrent.HandlerExecutor;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class MainActivity extends AppCompatActivity {

    Camera camera;
    ImageCapture imageCapture;
    ImageAnalysis imageAnalysis;
    TFliteClassifier tFliteClassifier=new TFliteClassifier();
    Preview preview;
    File outputDirectory;
    TextView result;
    ExecutorService cameraExecutor;
    private static final  String TAG = "CameraXBasic";
    private static final  String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final  int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA","android.permission.WRITE_EXTERNAL_STORAGE"};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        result=findViewById(R.id.result);
        if(allPermissionsGranted()){
            startCamera();}
        else{
            ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSIONS);
        }

        outputDirectory=getOutputDirectory();
        Button cameraButton=findViewById(R.id.camera_capture_button);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
        tFliteClassifier
                .intialize()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.e("Initialization","Success");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("Initialization","Error");
                    }
                });


    }
    public void takePhoto(){
        File photoFile= new File(outputDirectory, new SimpleDateFormat(FILENAME_FORMAT, Locale.ENGLISH).format(System.currentTimeMillis())+".jpg");
        ImageCapture.OutputFileOptions outputFileOptions= new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri path = Uri.fromFile(photoFile);
                String msg = "Image saved" + path.toString();
                Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this,"Error occured while saving",Toast.LENGTH_LONG).show();
                Log.e("Saving","Error:",exception);

            }
        });
    }
    public void startCamera(){
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture=ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider= null;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
            preview= new Preview.Builder()
                    .build();
            imageCapture=new ImageCapture.Builder()
                    .build();



            cameraExecutor= Executors.newSingleThreadExecutor();


            imageAnalysis=new ImageAnalysis.Builder()
                    .setBackgroundExecutor(ContextCompat.getMainExecutor(this))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();


            imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer(){

                @Override
                public void analyze(@NonNull ImageProxy image) {
                    @SuppressLint("UnsafeExperimentalUsageError") Image img=image.getImage();
                    Bitmap bitmap=toBitmap(img);


                    tFliteClassifier.classifyAsync(bitmap)
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e("Run","Failure");
                                }
                            })
                            .addOnSuccessListener(new OnSuccessListener<String>(){

                                @Override
                                public void onSuccess(String s) {
                                    result.setText(s);
                                    Log.e("Run","Success");
                                    image.close();



                                }


                            });




                }
            });
            CameraSelector cameraSelector= new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();
            try{
                assert cameraProvider != null;
                cameraProvider.unbindAll();
                camera=cameraProvider.bindToLifecycle(MainActivity.this,cameraSelector,preview,imageCapture,imageAnalysis);
                androidx.camera.view.PreviewView viewfinder = findViewById(R.id.viewFinder);
                preview.setSurfaceProvider(viewfinder.createSurfaceProvider(camera.getCameraInfo()));
            }
            catch (Exception e){
                Log.e(TAG, "Use case binding failed", e);
            }

        },ContextCompat.getMainExecutor(this)

        );

    }

    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
    public File getOutputDirectory(){
        File mediaDir=new File(Environment.getExternalStorageDirectory()+"/CameraX","MediaCaptured");
        if(!mediaDir.exists()){
            boolean direct=mediaDir.mkdirs();
        }

        return mediaDir;

    }
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode==REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted())
            {
                startCamera();
            }
            else{
                Toast.makeText(this,"Permission Not Granted By User",Toast.LENGTH_LONG).show();
            }
        }
    }
}
