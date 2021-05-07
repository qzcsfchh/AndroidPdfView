package io.github.qzcsfchh.android.pdf.test;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.qzcsfchh.android.pdf.PdfView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final PdfView pdfView = findViewById(R.id.pdfView);


//        File externalFilesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//        File file = new File(externalFilesDir, "mm.pdf");
//        pdfView.setPdfFilePath(file);


        new Thread(){
            @Override
            public void run() {

                InputStream open = null;
                FileOutputStream fos = null;
                try {
                    File cache = getExternalFilesDir("cache");
                    final File file = new File(cache, "mm.pdf");
                    if (!file.exists()) {
                        open = getAssets().open(file.getName());
                        fos = new FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int len = 0;
                        while ((len = open.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    pdfView.post(new Runnable() {
                        @Override
                        public void run() {
                            pdfView.setPdfFilePath(file);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    try {
                        if (open != null) {
                            open.close();
                        }
                        if (fos != null) {
                            fos.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }.start();

        // assets里面的pdf会被aapt损坏，导致无法渲染
//        pdfView.setPdfAssetsPath("cc.pdf");
    }


}