package edu.stanford.holyc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

/**
 * The Entry Activity of the Controller
 * Contains UIs for app settings, 
 * including which port the controller listens to and which interface the controller controls.
 *
 * @author Te-Yuan Huang (huangty@stanford.edu)
 *
 */

public class entryUI extends Activity {
	/** UI objects for the entry UI**/
	private Button button_startctl;
	private EditText field_port;
	private CheckBox wifi_checkbox;
	private CheckBox mobile_checkbox; //3G
	
	/** Whether or not to include wifi or mobile interfaces **/
	private boolean wifi_included = true;
	private boolean mobile_included = true;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entry);
        findViews();
        setListeners();
    }
    
    private void findViews(){
    	button_startctl = (Button)findViewById(R.id.start);
    	field_port = (EditText)findViewById(R.id.port);
    	wifi_checkbox = (CheckBox)findViewById(R.id.wifiCheckbox);
    	mobile_checkbox = (CheckBox)findViewById(R.id.mobileCheckbox);
    }
    private void setListeners(){
    	button_startctl.setOnClickListener(startOpenflowSwitchControlChannel);
    	wifi_checkbox.setOnClickListener(wifiIncludenessChanged);
    	mobile_checkbox.setOnClickListener(mobileIncludenessChanged);
    }    
    /**
     * Called When the "Start" button is clicked 
     * 
     * Do:
     * 1. Check if the port number is within [1024 - 65535]. 
     *    If yes, then start the controller; otherwise, staying in this activity.
     * 2. Pass (a)the port number (b)wifi_included (c) mobile_included to statusUI 
     */
    private Button.OnClickListener startOpenflowSwitchControlChannel = new Button.OnClickListener(){
    	public void onClick(View v){
    		int bind_port = -1;
    		try{
    			bind_port = Integer.parseInt(field_port.getText().toString());
    		}catch(Exception e){
    			Toast.makeText(entryUI.this, 
    					R.string.portRangeReminder, Toast.LENGTH_SHORT).show();
    		}
    		
    		if(bind_port < 1024 || bind_port > 65535){
    			Toast.makeText(entryUI.this, 
    					R.string.portRangeReminder, Toast.LENGTH_SHORT).show();
    		}else{
    			/** 
    			 * Port number is okay, pass it to statusUI
    			 * */
    			Intent intent = new Intent();
    			intent.setClass(entryUI.this, statusUI.class);
    			Bundle bundle = new Bundle();
    			bundle.putInt("BIND_PORT", bind_port);
    			bundle.putBoolean("WIFI_INCLUDED", wifi_included);
    			bundle.putBoolean("3G_INCLUDED", mobile_included);
    			intent.putExtras(bundle);
    			startActivity(intent);
    		}    		
    	}
    };
    
    /**
     *  Called when the "WiFi" checkbox is clicked     
     * */
    private CheckBox.OnClickListener wifiIncludenessChanged = new CheckBox.OnClickListener(){
        public void onClick(View v) {
            // Perform action on clicks, depending on whether it's now checked
            if (((CheckBox) v).isChecked()) {
                Toast.makeText(entryUI.this, "WiFi is included", Toast.LENGTH_SHORT).show();
                wifi_included = true;
            } else {
                Toast.makeText(entryUI.this, "WiFi is excluded", Toast.LENGTH_SHORT).show();
                wifi_included = false;
            }
        }
    };
    /**
     *  Called when the "3G" checkbox is clicked     
     * */
    private CheckBox.OnClickListener mobileIncludenessChanged = new CheckBox.OnClickListener(){
        public void onClick(View v) {
            // Perform action on clicks, depending on whether it's now checked
            if (((CheckBox) v).isChecked()) {
                Toast.makeText(entryUI.this, "3G is included", Toast.LENGTH_SHORT).show();
                mobile_included = true;
            } else {
                Toast.makeText(entryUI.this, "3G is excluded", Toast.LENGTH_SHORT).show();
                mobile_included = false;
            }
        }
    };
}