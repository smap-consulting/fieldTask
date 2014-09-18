package org.smap.smapTask.android.utilities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.tasks.DownloadFormsTask;
import org.odk.collect.android.tasks.DownloadFormsTask.FileResult;
import org.odk.collect.android.utilities.FileUtils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class ManageForm {
	
	private class FormDetails {
		 String formName = null;
	     String formPath = null;
	     String submissionUri = null;
	     boolean exists = false;
	}
	
	private FormDetails getFormDetails(String formId, String formVersionString) {
	
		FormDetails fd = new FormDetails();
   	 	Cursor c = null;
        
		try {
        	
        	String selectionClause = FormsColumns.JR_FORM_ID + "=? AND "
					+ FormsColumns.JR_VERSION + "=?";
        	
        	String [] selectionArgs = new String[] { formId, formVersionString };
        	//String [] selectionArgs = new String [1];
        	//selectionArgs[0] = formId;
        	String [] proj = {FormsColumns._ID, FormsColumns.DISPLAY_NAME, FormsColumns.JR_FORM_ID,
        			FormsColumns.SUBMISSION_URI,FormsColumns.FORM_FILE_PATH}; 
        	
        	final ContentResolver resolver = Collect.getInstance().getContentResolver();
        	c = resolver.query(FormsColumns.CONTENT_URI, proj, selectionClause, selectionArgs, null);
            
        	if(c.getCount() > 0) {
        		
            	// Form is already on the phone
	        	 c.moveToFirst();
	        	 String table_id = c.getString(c.getColumnIndex(FormsColumns._ID));
	             fd.formName = c.getString(c.getColumnIndex(FormsColumns.DISPLAY_NAME));
	             fd.submissionUri = c.getString(c.getColumnIndex(FormsColumns.SUBMISSION_URI));
	             fd.formPath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));
	             fd.exists = true;

	             Log.i("ManageForm: existing form found:", table_id +  
	            		 "--" + formId + "--" + fd.formName + "--" + 
	            		 fd.submissionUri + "--" + fd.formPath);
             
        	} else {
        		fd.exists = false;
        	}
		 } catch (Throwable e) {
       		 Log.e("ManageForm", e.getMessage());
    	 }
		c.close();
		
		return fd;
	}
	
	private boolean isIncompleteInstance(String formId, String version) {
		
		boolean isIncomplete = false;
   	 	Cursor c = null;
        
		try {
        	
			// get all complete or failed submission instances
			String selection = InstanceColumns.STATUS + "=? and "
					+ InstanceColumns.JR_FORM_ID + "=? and "  
					+ InstanceColumns.JR_VERSION + "=?";
			
			String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, 
					formId,
					version
					};

        	String [] proj = {InstanceColumns._ID}; 
        	
        	final ContentResolver resolver = Collect.getInstance().getContentResolver();
        	c = resolver.query(InstanceColumns.CONTENT_URI, proj, selection, selectionArgs, null);
            
        	if(c.getCount() > 0) {
        		
            	isIncomplete = true;
             
        	} 
		 } catch (Throwable e) {
       		 Log.e("ManageForm", e.getMessage());
    	 }
		c.close();
		
		return isIncomplete;
	}
        	
	     
	/*
	 * Parameters
	 *   formId:  	Stored as jrFormId in the forms database.
	 *   			Extracted by odk from the id attribute on top level data element in the downloaded xml
	 *   formURL:	URL to download the form
	 *   			Not stored
	 *   instanceDataURL:
	 *   			URL to download the data.
	 *   			Not stored
	 *   
	 *   Because odk uniquely identifies forms based on the id extracted from the down loaded xml file
	 *    the formId must be sourced from the task management system along with the URL so we can check
	 *    if the form has already been downloaded.  
	 */
    public ManageFormResponse insertForm(String formId, int formVersion, String formURL, String projectName) {

        String formVersionString = String.valueOf(formVersion);	
        
        ManageFormResponse mfResponse = new ManageFormResponse();
        
        Log.i("ManageForm: called:",  
       		 "formId:" + formId + ":" + formVersion + "-- formUrl:" + formURL);
        
    	FormDetails fd = getFormDetails(formId, formVersionString);    // Get the form details
		
    	if(!fd.exists) {	
        	 // Form was not found try downloading it
        	 FileResult dl = null;
        	 
        	 try {
        		 Log.i("ManageForm", "Downloading form");
        		 DownloadFormsTask dft = new DownloadFormsTask();
        		 dl = dft.downloadXform(formId, formURL);
        	 } catch (Exception e) {
        		 mfResponse.isError = true;
        		 mfResponse.statusMsg = "Unable to download form from " + formURL;
        		 return mfResponse;
        	 }
        	 

        	 try {
        		 Log.i("ManageForm", "Inserting new form into forms database");
        		 ContentValues v = new ContentValues();
        		 v.put(FormsColumns.FORM_FILE_PATH, dl.getFile().getAbsolutePath());
        		 Log.i("ManageForm abs path", dl.getFile().getAbsolutePath());

                 HashMap<String, String> formInfo = FileUtils.parseXML(dl.getFile());
                 v.put(FormsColumns.DISPLAY_NAME, formInfo.get(FileUtils.TITLE));
                 v.put(FormsColumns.JR_VERSION, formInfo.get(FileUtils.VERSION));
                 v.put(FormsColumns.JR_FORM_ID, formInfo.get(FileUtils.FORMID));
                 v.put(FormsColumns.PROJECT, formInfo.get(FileUtils.PROJECT));
                 v.put(FormsColumns.SUBMISSION_URI, formInfo.get(FileUtils.SUBMISSIONURI));
                 v.put(FormsColumns.BASE64_RSA_PUBLIC_KEY, formInfo.get(FileUtils.BASE64_RSA_PUBLIC_KEY));
        		
        		 
                 fd.formName = formInfo.get(FileUtils.TITLE);
                 fd.submissionUri = formInfo.get(FileUtils.SUBMISSIONURI);
                 fd.formPath = dl.getFile().getAbsolutePath();
                 formId = formInfo.get(FileUtils.FORMID);	// Update the formID with the actual value in the form (should be the same)
                 
                Log.i("ManageForm", "Form does not already exist, hence inserting now");
                Collect.getInstance().getContentResolver().insert(FormsColumns.CONTENT_URI, v);
               
                 
        	 } catch (Throwable e) {
           		 mfResponse.isError = true;
           		 Log.e("ManageForm", e.getMessage());
        		 mfResponse.statusMsg = "Unable to insert form "  + formURL + " into form database.";
      
        		 return mfResponse;
        	 }
            	 
    	}
         
         mfResponse.isError = false;
         mfResponse.formPath = fd.formPath;
         return mfResponse;
    }
    
    /*
     * Delete any forms not in the passed in HashMap unless there is an incomplete instance
     */
    public void deleteForms(HashMap <String, String> formMap, HashMap <String, String> results) {

        
   	 	Cursor c = null;
        
		try {
        	
        	//String [] selectionArgs = new String [1];
        	//selectionArgs[0] = formId;
        	String [] proj = {FormsColumns._ID, FormsColumns.JR_FORM_ID, FormsColumns.JR_VERSION}; 
        	
        	final ContentResolver resolver = Collect.getInstance().getContentResolver();
        	c = resolver.query(FormsColumns.CONTENT_URI, proj, null, null, null);
            
        	ArrayList<Long> formsToDelete = new ArrayList<Long> ();
        	if(c.getCount() > 0) {
        		
	        	 
	        	 while(c.moveToNext()) {
		        	 Long table_id = c.getLong(c.getColumnIndex(FormsColumns._ID));
		             String formId = c.getString(c.getColumnIndex(FormsColumns.JR_FORM_ID));
		             String version = c.getString(c.getColumnIndex(FormsColumns.JR_VERSION));

		             Log.i("ManageForm: existing form found:", table_id +  
	            		 "--" + formId + "--" + version);
		             
		             // Check to see if this form was downloaded
		             if(formMap.get(formId + "_v_" + version) == null) {
		            	 Log.i("   Delete: ", "Candidate 1");
		            	 if(!isIncompleteInstance(formId, version)) {
		            		 Log.i("   Delete: ", "Candidate 2 !!!!!!!!!!!");
		            		 formsToDelete.add(table_id);
		            	 }
		             } else {
		            	 Log.i("   Don't Delete: ", "Keep this one");
		             }
	        	 }
	        	 if(formsToDelete.size() > 0) {
	        			
	        			Long[] formArray = formsToDelete.toArray(new Long[formsToDelete.size()]);
	        			// delete files from database and then from file system
	        			for (int i = 0; i < formArray.length; i++) {
	        				
	        				try {
	        		            Uri deleteForm =
	        		                Uri.withAppendedPath(FormsColumns.CONTENT_URI, formArray[i].toString());
	        		            
	        		            int wasDeleted = resolver.delete(deleteForm, null, null); 
	        		            
	        		            if (wasDeleted > 0) {
	        		            	Collect.getInstance().getActivityLogger().logAction(this, "delete", deleteForm.toString());
	        		            }
	        				} catch ( Exception ex ) {
	        					Log.e("Error deleting forms: "," during delete of: " + formArray[i].toString() + " exception: "  + ex.toString());
	        					results.put("Error " + formArray[i].toString() + ": ", " during delete of form "  + " : " + ex.toString());
	        				}
	        		    } 
	
	        	 }
             
        	} 
		 } catch (Throwable e) {
       		 Log.e("ManageForm", e.getMessage());
    	 }
		c.close();
         
    }
    
    /*
	 * Parameters
	 *   formId:  	Stored as jrFormId in the forms database.
	 *   			Extracted by odk from the id attribute on top level data element in the downloaded xml
	 *   formURL:	URL to download the form
	 *   			Not stored
	 *   instanceDataURL:
	 *   			URL to download the data.
	 *   			Not stored
	 *    
	 */
    public ManageFormResponse insertInstance(String formId, int formVersion, String formURL, String initialDataURL, String taskId) {
     
        String instancePath = null;
        String formVersionString = String.valueOf(formVersion);	
        
        ManageFormResponse mfResponse = new ManageFormResponse();
        
        Log.i("ManageForm: insert instance called:",  
       		 "formId:" + formId + ":" + formVersion + "-- formUrl:" + formURL + "-- initialDataUrl:" + initialDataURL);
        
    	FormDetails fd = getFormDetails(formId, formVersionString);    // Get the form details
		
    	if(fd.exists) {
         
	  		 // Get the instance path
	         instancePath = getInstancePath(fd.formPath, taskId);
	         if(instancePath != null && initialDataURL != null) {
	        	 Log.i("ManageForm Instance Path:", instancePath);
	        	 File f = null;
	
	             f = new File(instancePath);
	             DownloadFormsTask dft = new DownloadFormsTask();
	             try {
	             dft.downloadFile(f, initialDataURL);
	             } catch (Exception e) {
	            	 e.printStackTrace();
	         		 mfResponse.isError = true;
	            	 mfResponse.statusMsg = "Unable to download initial data from " + initialDataURL + " into file: " + instancePath;
	         		 return mfResponse;
	             }
	         }
	
			    
	         // Write the new instance entry into the instance content provider
	         try {
	        	 mfResponse.mUri = writeInstanceDatabase(formId, formVersionString, fd.formName, fd.submissionUri, instancePath);
	         } catch (Throwable e) {
	        	 e.printStackTrace();
	       		 mfResponse.isError = true;
	    		 mfResponse.statusMsg = "Unable to insert instance " + formId + " into instance database.";
	        	 return mfResponse;
	         }
    	} else {
            mfResponse.isError = true;
            mfResponse.formPath = fd.formPath;
            mfResponse.instancePath = instancePath;
    	}
         
         mfResponse.isError = false;
         mfResponse.formPath = fd.formPath;
         mfResponse.instancePath = instancePath;
         return mfResponse;
    }
    
    private Uri writeInstanceDatabase(String jrformid, String jrVersion, String formName, 
			String submissionUri, String instancePath) throws Throwable {
    
    	Log.i("InstanceCreate", "Inserting new instance into database");
    	ContentValues values = new ContentValues();
	 
    	values.put(InstanceColumns.JR_FORM_ID, jrformid);
    	values.put(InstanceColumns.JR_VERSION, jrVersion);
    	values.put(InstanceColumns.SUBMISSION_URI, submissionUri);
    	values.put(InstanceColumns.INSTANCE_FILE_PATH, instancePath);
    	values.put(InstanceColumns.DISPLAY_NAME, formName);
    	values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INCOMPLETE);
    	//values.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, Boolean.toString(false));
	
    	return Collect.getInstance().getContentResolver()
    			.insert(InstanceColumns.CONTENT_URI, values);
    }
    
    /*
     * Instance path is based on basepath, filename, timestamp and the task id
     * Paramters
     *  formPath:   Used to obtain the filename
     *  taskId:	    Used to guarantee uniqueness when multiple tasks for the same form are assigned
     */
    private String getInstancePath(String formPath, String taskId) {
        String instancePath = null;
        
        if(formPath != null) {
	    	String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
	                        .format(Calendar.getInstance().getTime());
            String file =
                formPath.substring(formPath.lastIndexOf('/') + 1, formPath.lastIndexOf('.'));
            String path = Collect.INSTANCES_PATH + "/" + file + "_" + time + "_" + taskId;
            if (FileUtils.createFolder(path)) {
                instancePath = path + "/" + file + "_" + time + ".xml";
            }
        }
            
        return instancePath;
    }

}
