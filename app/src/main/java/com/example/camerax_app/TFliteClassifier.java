package com.example.camerax_app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.tasks.Task;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static androidx.camera.core.CameraX.getContext;
import static com.google.android.gms.tasks.Tasks.call;

public class TFliteClassifier {
    Interpreter interpreter;
    GpuDelegate gpuDelegate;
    boolean isIntialised=false;

    ExecutorService executorService= Executors.newCachedThreadPool();

    private ArrayList<String> labelList=new ArrayList<>();
    int inputImageWidth;
    int inputImageHeight;
    int modelInputSize;
    private final String TAG = "TfliteClassifier";
    private final int FLOAT_TYPE_SIZE = 4;
    private final int CHANNEL_SIZE = 3;
    private final float IMAGE_MEAN = 127.5f;
    private final float IMAGE_STD = 127.5f;


    private ByteBuffer loadModelFile(AssetManager assetManager,String filename) throws IOException {
        AssetFileDescriptor fileDescripter = assetManager.openFd(filename);
        FileInputStream fileInputStream= new FileInputStream(fileDescripter.getFileDescriptor());
        FileChannel channel = fileInputStream.getChannel();
        long startoffset=fileDescripter.getStartOffset();
        long declaredLength=fileDescripter.getDeclaredLength();
        return channel.map(FileChannel.MapMode.READ_ONLY,startoffset,declaredLength);
    }
    private ArrayList<String> loadLabel(Context context, String filename) throws IOException{
        Scanner s= new Scanner(new InputStreamReader(context.getAssets().open(filename)));
        ArrayList<String> labels=new ArrayList<>();
        while(s.hasNext())
        {
            labels.add(s.nextLine());
        }
        return labels;
    }

    Task<Void> intialize(){
        return call(executorService, new Callable<Void>(){

            @Override
            public Void call() throws Exception {
                initializeInterpreter();
                return null;
            }
        });
    }
    @SuppressLint("RestrictedApi")
    private void initializeInterpreter() throws IOException {

        @SuppressLint("RestrictedApi") AssetManager assetManager=getContext().getAssets();
        ByteBuffer model = loadModelFile(assetManager, "converted_model.tflite");
        labelList=loadLabel(getContext(),"labels.txt");
        gpuDelegate=new GpuDelegate();
        Interpreter.Options options = new Interpreter.Options();
        options.addDelegate(gpuDelegate);
        interpreter=new Interpreter(model,options);
        int[] inputShape = interpreter.getInputTensor(0).shape();
        inputImageHeight=inputShape[2];
        inputImageWidth=inputShape[1];
        modelInputSize=4*inputImageWidth*inputImageHeight*3;
        isIntialised=true;
    }
    private String classify(Bitmap bitmap){
        String result;
        if(isIntialised)
        {
            Bitmap resizedImage=Bitmap.createScaledBitmap(bitmap,inputImageWidth,inputImageHeight,true);
            ByteBuffer byteBuffer=convertBitmapToBuffer(resizedImage);
            Log.d("Classify","Loading");
            long startTime = SystemClock.uptimeMillis();
            float[][] output= new float[1][labelList.size()];
            interpreter.run(byteBuffer,output);
            long endTime=SystemClock.uptimeMillis();
            long inferenceTime=endTime-startTime;
            int index=getMaxResult(output[0]);
            result="Predicted class:"+ labelList.get(index) +"\nInference Time:"+inferenceTime;
        }
        else{
            result="Error";
        }
        return result;
    }

    private int getMaxResult(float[] floats) {
        float probablity=0;
        int index=0;
        for (int i=0;i<labelList.size();i++)
        {
            if(probablity<floats[i])
            {
                probablity=floats[i];
                index=i;
            }

        }
        return index;

    }

    private ByteBuffer convertBitmapToBuffer(Bitmap bitmap){
        ByteBuffer byteBuffer=ByteBuffer.allocateDirect(modelInputSize);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] pixels=new int[inputImageHeight*inputImageWidth];
        bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        int pixel=0;
        for (int i = 0; i < inputImageWidth; i++) {
            for (int j = 0; j < inputImageHeight; j++) {
                final int val = pixels[pixel++];
                byteBuffer.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                byteBuffer.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                byteBuffer.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
            }
        }
        bitmap.recycle();
        return byteBuffer;

    }
    Task<String> classifyAsync(Bitmap bitmap){
        return call(executorService,new Callable<String>(){

            @Override
            public String call() throws Exception {
               return classify(bitmap);
            }
        });
    }



}
