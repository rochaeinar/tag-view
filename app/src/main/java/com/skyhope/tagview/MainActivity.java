package com.skyhope.tagview;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.skyhope.materialtagview.TagView;


public class MainActivity extends AppCompatActivity {

    TagView tagView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tagView = findViewById(R.id.tag_view_test);

        tagView.setHint("Add your skill");

        //tagView.addTagSeparator(TagSeparator.AT_SEPARATOR);

        tagView.addTagLimit(5);

       // tagView.setTagBackgroundColor(Color.RED);

        String[] tagList = new String[]{"C++", "Java", "PHP", "2 Tesalonicenses","2 Tesalonicenses"};
        tagView.setTagList(tagList);
        tagView.setTagBackgroundColor(getResources().getColor(R.color.colorAccent));
        tagView.setFocusable(false);

    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}
