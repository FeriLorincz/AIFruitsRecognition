package com.example.objectdetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;


import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult;

import java.io.FileDescriptor;
import java.io.IOException;
import java.security.Permission;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ObjectDetectorHelper.DetectorListener {

    private static final int RESULT_LOAD_IMAGE = 123;
    public static final int IMAGE_CAPTURE_CODE = 654;
    private static final int PERMISSION_CODE = 321;
    ImageView innerImage;
    private Uri image_uri;
    private final float CONFIDENCE_THRESHOLD = 0.3f;

    ObjectDetectorHelper objectDetectorHelper;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        innerImage = findViewById(R.id.imageView2);
        innerImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, RESULT_LOAD_IMAGE);
            }
        });

        innerImage.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_DENIED){
                        String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                        requestPermissions(permission, PERMISSION_CODE);
                    }
                    else {
                        openCamera();
                    }
                }

                else {
                    openCamera();
                }
                return false;
            }
        });

        objectDetectorHelper = new ObjectDetectorHelper(0.5f,ObjectDetectorHelper.MAX_RESULTS_DEFAULT,ObjectDetectorHelper.DELEGATE_CPU,"fruits.tflite", RunningMode.IMAGE,getApplicationContext(),this);


    }

    //open camera to capture picture
    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null){
            image_uri = data.getData();
            doInference();
        }

        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK){
            doInference();
        }
    }

    //Passing image to the model, getting the results and drawing rectangles
    public void doInference() {
        Bitmap bitmap = uriToBitmap(image_uri);

        // Verifică și corectează orientarea imaginii
        ExifInterface exif;
        try {
            ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(image_uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            exif = new ExifInterface(fileDescriptor);

            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);  // Schimbat din -90 în 90
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);  // Schimbat din -180 în 180
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);  // Schimbat din -270 în 270
                    break;
            }

            if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            parcelFileDescriptor.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        innerImage.setImageBitmap(bitmap);
        Bitmap mutableBmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBmp);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((mutableBmp.getWidth() / 95));

        Paint paintText = new Paint();
        paintText.setColor(Color.BLUE);
        paintText.setTextSize(mutableBmp.getWidth()/10);

        ObjectDetectorHelper.ResultBundle resultBundle = objectDetectorHelper.detectImage(bitmap);
        if(resultBundle != null) {
            Log.d("tryRess","results are not null");
            List<ObjectDetectionResult> resultList = resultBundle.getResults();
            for (ObjectDetectionResult singleResult: resultList) {
                List<Detection> detectionList = singleResult.detections();
                for(Detection detection : detectionList) {
                    float confidence = 0;
                    String objectName = "";
                    for(Category category : detection.categories()) {
                        if(category.score() > confidence) {
                            confidence = category.score();
                            objectName = category.categoryName();
                        }
                    }
                    canvas.drawRect(detection.boundingBox(), paint);
                    canvas.drawText(objectName, detection.boundingBox().left, detection.boundingBox().top, paintText);
                }
            }
            innerImage.setImageBitmap(mutableBmp);
        } else {
            Log.d("tryRess","results are null");
        }
    }


    //TODO rotate image if image captured on sumsong devices
    //Most phone cameras are landscape, meaning if you take the photo in portrait, the resulting photos will be rotated 90 degrees.
    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input){
        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
        Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);
        int orientation = -1;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
        }
        Log.d("tryOrientation",orientation+"");
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(orientation);
        Bitmap cropped = Bitmap.createBitmap(input,0,0, input.getWidth(), input.getHeight(), rotationMatrix, true);
        return cropped;
    }


    //takes image uri as input and return that image in a bitmap format
    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  null;
    }

    //If user gives permission then launch camera
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSION_CODE && grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            openCamera();
        }
    }

    @Override
    public void onError(String var1, int var2) {

    }

    @Override
    public void onResults(ObjectDetectorHelper.ResultBundle var1) {

    }
}
