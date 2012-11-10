
package org.drykiss.android.app.ashfa;

import org.drykiss.android.app.ashfa.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class AshFAActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getApplicationContext().startService(new Intent(this, AshFAService.class));
    }
}
