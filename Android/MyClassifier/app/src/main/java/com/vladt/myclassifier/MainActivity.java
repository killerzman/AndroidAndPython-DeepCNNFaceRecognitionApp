package com.vladt.myclassifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.theartofdev.edmodo.cropper.CropImage;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    //variables used for the layout elements
    private ImageView imgCapturedPhoto;
    private TextView txtviewClassesRecognized;
    private Spinner spinnerModelSelection;
    private Button btnSelectPhoto;
    private Button btnRecognizePhoto;
    private File fileCameraOutput;

    //variables used for memorizing the selected model and its' corresponding label
    private String stringSelectedModel;
    private String stringSelectedLabel;

    //variables used for resizing and normalizing the image
    int inputSize = 128;
    private static int IMAGE_MEAN = 0;
    private static float IMAGE_STD = 1.0f;

    //Application starts here
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get the layout elements
        imgCapturedPhoto = findViewById(R.id.CapturedPhoto);
        txtviewClassesRecognized = findViewById(R.id.ClassesRecognized);
        spinnerModelSelection = findViewById(R.id.ModelSelection);
        btnSelectPhoto = findViewById(R.id.SelectPhoto);
        btnRecognizePhoto = findViewById(R.id.RecognizePhoto);

        //create a listener for the spinner selector
        spinnerModelSelection.setOnItemSelectedListener(this);

        //create a listener for the "select photo" button
        btnSelectPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //if pressed, show a dialog
                selectPhotoDialog();
            }
        });

        //create a listener for the "recognize photo" button
        btnRecognizePhoto.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                try {
                    //if pressed, try to recognize the photo
                    recognizePhoto();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //populate the spinner list with the models from the assets folder
        try {
            //get the models from the assets folder
            List<String> models = getModelsFromAssets();
            //create a simple adapter for the spinner
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, models);
            //setting up the spinner options
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            //spinner layout element uses the created adapter
            spinnerModelSelection.setAdapter(spinnerAdapter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //function spawns a dialog that makes you add a photo from camera or gallery
    public void selectPhotoDialog(){
        //creating the choices used for the dialog
        final CharSequence[] choices = {"Take A Photo", "Choose From Gallery", "Cancel"};
        //creating a new dialog
        AlertDialog.Builder choicesBuilder = new AlertDialog.Builder(MainActivity.this);
        choicesBuilder.setTitle("Add A Photo");
        //populating the dialog with the choices
        choicesBuilder.setItems(choices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //if the choice is "Take A Photo"
                if(choices[which].equals("Take A Photo")){
                    //request camera permission
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, RequestCodes.Codes.CAMERA_PERMISSION_CODE.ordinal());
                    }
                    //if permission is already granted, take a photo
                    else{
                        try {
                            takeAPhoto();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //if the choice is "Choose from Gallery
                else if(choices[which].equals("Choose From Gallery")){
                    //create an intent to pick a photo from the phone's gallery
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    //start the intent
                    startActivityForResult(intent, RequestCodes.Codes.CHOOSE_FROM_GALLERY_CODE.ordinal());
                }
                //if the choice is "Cancel"
                else if(choices[which].equals("Cancel")){
                    //close the dialog
                    dialog.dismiss();
                }
            }
        });
        //show the dialog
        choicesBuilder.show();
    }

    @SuppressLint("SetTextI18n")
    private void recognizePhoto() throws IOException {
        //if no photo was chosen
        if(imgCapturedPhoto.getDrawable() == null){
            //alert the user to select a photo first
            Toast.makeText(this, "Select a photo first!", Toast.LENGTH_LONG).show();
        }
        else{
            //create the interpreter that loads up the selected model with default options
            Interpreter interpreter = new Interpreter(loadModelFile(getAssets(), stringSelectedModel), new Interpreter.Options());
            //get the label list from the model's corresponding label file
            List<String> labelList = loadLabelList(getAssets(), stringSelectedLabel);
            //get the bitmap from the photo
            Bitmap bm = ((BitmapDrawable)imgCapturedPhoto.getDrawable()).getBitmap();
            //resize the bitmap to the same image size that the model used when training
            Bitmap resizedBM = Bitmap.createScaledBitmap(bm, inputSize, inputSize, false);
            //convert the resized bitmap to a byte buffer
            ByteBuffer byteBuffer = convertBitmapToByteBuffer(resizedBM);
            //create an array to save the confidence result for each label
            float [][]result = new float[1][labelList.size()];
            //get timestamp before evaluating the image
            long t1 = System.currentTimeMillis();
            //run the interpreter on the buffer and save the results in the array from above
            interpreter.run(byteBuffer, result);
            //get timestamp after the image was evaluated
            long t2 = System.currentTimeMillis();
            //sort the result array from highest to lowest confidence
            for(int i = 0; i < labelList.size() - 1; i++){
                for(int j = i + 1; j < labelList.size(); j++){
                    if(result[0][j] > result[0][i]){
                        float aux = result[0][j];
                        result[0][j] = result[0][i];
                        result[0][i] = aux;
                        //sort the label list after same rules
                        Collections.swap(labelList, i, j);
                    }
                }
            }
            //show the top 3 classes recognized and the time taken for recognizing the image
            txtviewClassesRecognized.setText(
                "Top 3 classes recognized:\n" +
                labelList.get(0) + " : " + result[0][0] * 100 + "%\n" +
                labelList.get(1) + " : " + result[0][1] * 100 + "%\n" +
                labelList.get(2) + " : " + result[0][2] * 100 + "%\n" +
                "Action took " + (t2-t1) + " ms."
            );
        }
    }

    //load "deep learning" model
    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //get the label list from the assets folder
    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        //create an empty list to populate with the labels
        List<String> labelList = new ArrayList<>();
        //create a reader on the label file
        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        //read all the lines from the label file
        while ((line = reader.readLine()) != null) {
            //and add them to the list
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;
        //buffer is allocated by 3 rules:
        //1. there are 4 bytes in a float
        //2. the image input size
        //3. how many channels the image has -> 3
        byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3);

        //order the bytes in the buffer in their native order
        byteBuffer.order(ByteOrder.nativeOrder());

        //create an array to get the bitmap's rgb values
        int[] intValues = new int[inputSize * inputSize];
        //get the rgb values from the bitmap and store them in the array above
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        //variable to store the current pixel's index to process
        int pixel = 0;
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                //get the rgb value
                final int val = intValues[pixel++];
                //the rgb integer must be processed to get all the 3 channels pixel values from the original image
                //the rgb integer value looks like: 0xRRGGBB
                //after getting the pixel values, they are normalized according to the pixel values interval from the training process of the models
                //get the r pixel value
                byteBuffer.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                //get the g pixel value
                byteBuffer.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                //get the b pixel value
                byteBuffer.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
            }
        }
        return byteBuffer;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //if the camera permission is asked for
        if (requestCode == RequestCodes.Codes.CAMERA_PERMISSION_CODE.ordinal()) {
            //and if the permission is granted
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    takeAPhoto();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                //alert the user to allow camera permission to take a photo
                Toast.makeText(this, "Allow camera permission to take a photo!", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            //if the user asked to capture a photo
            if(requestCode == RequestCodes.Codes.IMAGE_CAPTURE_CODE.ordinal()){
                //get the uri of the photo from the temporary camera output file
                Uri uri = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".provider", fileCameraOutput);
                cropPhoto(uri);
            }
            else if(requestCode == RequestCodes.Codes.CHOOSE_FROM_GALLERY_CODE.ordinal()){
                //get the uri of the photo chosen from the gallery intent
                Uri uri = data.getData();
                cropPhoto(uri);
            }
            else if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
                //get the result from the cropping activity
                CropImage.ActivityResult result = CropImage.getActivityResult(data);
                //set the image layout element to the result's uri above
                imgCapturedPhoto.setImageURI(result.getUri());
            }
        }
    }

    private void cropPhoto(Uri uri) {
        //start a cropping activity and save the cropped photo
        CropImage.activity(uri).setInitialCropWindowPaddingRatio(0).start(this);
    }

    public void takeAPhoto() throws IOException {
        //start a image capture intent
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //get the current timestamp
        @SuppressLint("SimpleDateFormat") String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        //get the pictures directory
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        //create a temporary file in the pictures directory
        fileCameraOutput = File.createTempFile(timestamp, ".jpg", storageDir);
        //save the photo in the temporary file
        Uri photoURI = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".provider", fileCameraOutput);
        //send the uri in the next intent so the photo can be saved in the image layout element
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        //start the next intent
        startActivityForResult(intent, RequestCodes.Codes.IMAGE_CAPTURE_CODE.ordinal());
    }

    public List<String> getModelsFromAssets() throws IOException {
        //create an empty files list
        List<String> files = new ArrayList<>();
        //get the assets list from the assets root folder
        String[] list = getAssets().list("");
        assert list != null;
        for(String file: list){
            //populate the empty files list with the "deep learning" converted models
            if(file.endsWith(".tflite")) {
                files.add(file);
            }
        }
        return files;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
        //get the selected item from the spinner element and save its' model name in a variable
        stringSelectedModel = parent.getItemAtPosition(position).toString();
        //save models' corresponding label name in a variable
        stringSelectedLabel = "labels" + stringSelectedModel.substring(stringSelectedModel.indexOf('_'), stringSelectedModel.lastIndexOf('_')) + ".txt";
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

}
