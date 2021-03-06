/* Copyright 2014 ESRI
 *
 * All rights reserved under the copyright laws of the United States
 * and applicable international laws, treaties, and conventions.
 *
 * You may freely redistribute and use this sample code, with or
 * without modification, provided you include the original copyright
 * notice and use restrictions.
 *
 * See the Sample code usage restrictions document for further information.
 *
 */

package com.arcgis.android.samples.search.placesearch;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SearchView.OnSuggestionListener;
import android.widget.SimpleCursorAdapter;

import com.esri.android.map.MapView;
import com.esri.android.map.event.OnStatusChangedListener;
import com.esri.android.toolkit.map.MapViewHelper;
import com.esri.core.map.CallbackListener;
import com.esri.core.tasks.geocode.Locator;
import com.esri.core.tasks.geocode.LocatorFindParameters;
import com.esri.core.tasks.geocode.LocatorGeocodeResult;
import com.esri.core.tasks.geocode.LocatorSuggestionParameters;
import com.esri.core.tasks.geocode.LocatorSuggestionResult;

import java.util.List;

/**
 * PlaceSearch app uses the geocoding service to convert addresses to and from
 * geographic coordinates and place them on the map.  Search for places, addresses,
 * etc. and get suggestions as you type.
 *
 */
public class MainActivity extends Activity {

  private static final String TAG = "PlaceSearch";
  private static final String COLUMN_NAME_ADDRESS = "address";
  private static final String COLUMN_NAME_X = "x";
  private static final String COLUMN_NAME_Y = "y";
  private static final String LOCATION_TITLE = "Location";
  private static final String FIND_PLACE = "Find";
  private static final String SUGGEST_PLACE = "Suggest";
  private static final String SUGGESTION_ADDRESS_DELIMNATOR = ", ";

  private MapView mMapView;
  private String mMapViewState;
  // Entry point to ArcGIS for Android Toolkit
  private MapViewHelper mMapViewHelper;

  private Locator mLocator;
  private SearchView mSearchView;
  private MenuItem searchMenuItem;
  private MatrixCursor mSuggestionCursor;

  private static ProgressDialog mProgressDialog;
  private LocatorSuggestionParameters suggestParams;
  private LocatorFindParameters findParams;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Setup and show progress dialog
    mProgressDialog = new ProgressDialog(this) {
      @Override
      public void onBackPressed() {
        // Back key pressed - just dismiss the dialog
        mProgressDialog.dismiss();
      }
    };

    // After the content of this activity is set the map can be accessed from the layout
    mMapView = (MapView) findViewById(R.id.map);
    // Initialize the helper class to use the Toolkit
    mMapViewHelper = new MapViewHelper(mMapView);
    // Create the default ArcGIS online Locator. If you want to provide your own {@code Locator},
    // user other methods of Locator.
    mLocator = Locator.createOnlineLocator();

    // set logo and enable wrap around
    mMapView.setEsriLogoVisible(true);
    mMapView.enableWrapAround(true);

    // Setup listener for map initialized
    mMapView.setOnStatusChangedListener(new OnStatusChangedListener() {

      @Override
      public void onStatusChanged(Object source, STATUS status) {
        if (source == mMapView && status == STATUS.INITIALIZED) {

          if (mMapViewState == null) {
            Log.i(TAG, "MapView.setOnStatusChangedListener() status=" + status.toString());
          } else {
            mMapView.restoreState(mMapViewState);
          }

        }
      }
    });

  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    if (id == R.id.action_search) {
      searchMenuItem = item;
      // Create search view and display on the Action Bar
      initSearchView();
      item.setActionView(mSearchView);
      return true;
    } else if (id == R.id.action_clear) {
      // Remove all the marker graphics
      if (mMapViewHelper != null) {
        mMapViewHelper.removeAllGraphics();
      }
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    if ((mSearchView != null) && (!mSearchView.isIconified())) {
      // Close the search view when tapping back button
      if (searchMenuItem != null) {
        searchMenuItem.collapseActionView();
        invalidateOptionsMenu();
      }
    } else {
      super.onBackPressed();
    }
  }


  // Initialize suggestion cursor
  private void initSuggestionCursor() {
    String[] cols = new String[]{BaseColumns._ID, COLUMN_NAME_ADDRESS, COLUMN_NAME_X, COLUMN_NAME_Y};
    mSuggestionCursor = new MatrixCursor(cols);
  }

  // Set the suggestion cursor to an Adapter then set it to the search view
  private void applySuggestionCursor() {
    String[] cols = new String[]{COLUMN_NAME_ADDRESS};
    int[] to = new int[]{R.id.suggestion_item_address};

    SimpleCursorAdapter mSuggestionAdapter = new SimpleCursorAdapter(mMapView.getContext(), R.layout.suggestion_item, mSuggestionCursor, cols, to, 0);
    mSearchView.setSuggestionsAdapter(mSuggestionAdapter);
    mSuggestionAdapter.notifyDataSetChanged();
  }

  // Initialize search view and add event listeners to handle query text changes and suggestion
  private void initSearchView() {
    if (mMapView == null || !mMapView.isLoaded())
      return;

    mSearchView = new SearchView(this);
    mSearchView.setFocusable(true);
    mSearchView.setIconifiedByDefault(false);
    mSearchView.setQueryHint(getResources().getString(R.string.search_hint));

    mSearchView.setOnQueryTextListener(new OnQueryTextListener() {

      @Override
      public boolean onQueryTextSubmit(String query) {
        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        getSuggestions(newText);
        return true;
      }
    });

    mSearchView.setOnSuggestionListener(new OnSuggestionListener() {

      @Override
      public boolean onSuggestionSelect(int position) {
        return false;
      }

      @Override
      public boolean onSuggestionClick(int position) {
        // Obtain the content of the selected suggesting place via cursor
        MatrixCursor cursor = (MatrixCursor) mSearchView.getSuggestionsAdapter().getItem(position);
        int indexColumnSuggestion = cursor.getColumnIndex(COLUMN_NAME_ADDRESS);
        int indexColumnX = cursor.getColumnIndex(COLUMN_NAME_X);
        int indexColumnY = cursor.getColumnIndex(COLUMN_NAME_Y);
        String address = cursor.getString(indexColumnSuggestion);
        double x = cursor.getDouble(indexColumnX);
        double y = cursor.getDouble(indexColumnY);

        if ((x == 0.0) && (y == 0.0)) {
          // Place has not been located. Find the place
          int index = address.indexOf(SUGGESTION_ADDRESS_DELIMNATOR);
          if (index > 0) {
            locatorParams(FIND_PLACE, address.substring(index + SUGGESTION_ADDRESS_DELIMNATOR.length()));
            new FindPlaceTask(mLocator).execute(findParams);
          } else {
            locatorParams(FIND_PLACE, address);
            new FindPlaceTask(mLocator).execute(findParams);
          }
        } else {
          // Place has been located. show the search result
          displaySearchResult(x, y, address);
        }
        cursor.close();

        return true;
      }
    });
  }

  // Find the address
  private class FindPlaceTask extends AsyncTask<LocatorFindParameters, Void, List<LocatorGeocodeResult>> {
    private final Locator mLocator;

    public FindPlaceTask(Locator locator) {
      mLocator = locator;
    }

    @Override
    protected List<LocatorGeocodeResult> doInBackground(LocatorFindParameters... params) {

        // Execute the task
        List<LocatorGeocodeResult> results = null;
        try {
          results = mLocator.find(params[0]);
        } catch (Exception e) {
          e.printStackTrace();
        }

        return results;

    }

    @Override
    protected void onPreExecute() {
      // Display progress dialog on UI thread
      mProgressDialog.setMessage(getString(R.string.address_search));
      mProgressDialog.show();
    }

    @Override
    protected void onPostExecute(List<LocatorGeocodeResult> results) {
      // Dismiss progress dialog
      mProgressDialog.dismiss();
      if ((results == null) || (results.size() == 0))
        return;

      // Add the first result to the map and zoom to it
      LocatorGeocodeResult result = results.get(0);
      double x = result.getLocation().getX();
      double y = result.getLocation().getY();
      String address = result.getAddress();

      displaySearchResult(x,y,address);
      hideKeyboard();
    }
  }


  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    super.onPause();

    mMapViewState = mMapView.retainState();
    mMapView.pause();
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Start the MapView running again
    if (mMapView != null) {
      mMapView.unpause();
      if (mMapViewState != null) {
        mMapView.restoreState(mMapViewState);
      }
    }
  }

  /**
   * Provide character-by-character suggestions for the search string
   *
   * @param suggestText String the user typed so far to fetch the suggestions
   */
  protected void getSuggestions(String suggestText) {
    final CallbackListener<List<LocatorSuggestionResult>> suggestCallback = new CallbackListener<List<LocatorSuggestionResult>>() {
        @Override
        public void onCallback(List<LocatorSuggestionResult> locatorSuggestionResults) {
          final List<LocatorSuggestionResult> locSuggestionResults = locatorSuggestionResults;
            if (locatorSuggestionResults == null)
                return;
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              int key = 0;
              if(locSuggestionResults.size() > 0) {
                // Add suggestion list to a cursor
                initSuggestionCursor();
                for (LocatorSuggestionResult result : locSuggestionResults) {
                  mSuggestionCursor.addRow(new Object[]{key++, result.getText(), "0", "0"});
                }
                applySuggestionCursor();
              }
            }

          });

        }


        @Override
        public void onError(Throwable throwable) {
            //Log the error
            Log.e(MainActivity.class.getSimpleName(), "No Results found!!");
            Log.e(MainActivity.class.getSimpleName(), throwable.getMessage());
        }
    };

      try {
            // Initialize the LocatorSuggestion parameters
            locatorParams(SUGGEST_PLACE,suggestText);

            mLocator.suggest(suggestParams, suggestCallback);

      } catch (Exception e) {
          Log.e(MainActivity.class.getSimpleName(),"No Results found");
          Log.e(MainActivity.class.getSimpleName(),e.getMessage());
      }
  }

  /**
   * Initialize the LocatorSuggestionParameters or LocatorFindParameters
   *
   * @param type A String determining the type of parameters to be initialized
   * @param query The string for which the locator parameters are to be initialized
   */
  protected void locatorParams(String type, String query) {
    if(type.contentEquals(SUGGEST_PLACE)) {
        suggestParams = new LocatorSuggestionParameters(query);
        // Use the centre of the current map extent as the find location point
        suggestParams.setLocation(mMapView.getCenter(), mMapView.getSpatialReference());
        // Set the radial search distance in meters
        suggestParams.setDistance(500.0);
    }
    else {
        findParams = new LocatorFindParameters(query);
        // Use the centre of the current map extent as the find location point
      findParams.setLocation(mMapView.getCenter(), mMapView.getSpatialReference());
        // Set the radial search distance in meters
        findParams.setDistance(500.0);
    }

  }

  /**
   * Display the search location on the map
   * @param x Longitude of the place
   * @param y Latitude of the place
   * @param address The address of the location
   */
  protected void displaySearchResult(double x, double y, String address) {
    // Add a marker at the found place. When tapping on the marker, a Callout with the address
    // will be displayed
    mMapViewHelper.addMarkerGraphic(y, x, LOCATION_TITLE, address, android.R.drawable.ic_menu_myplaces, null, false, 1);
    mMapView.centerAndZoom(y, x, 14);
    mSearchView.setQuery(address, true);

  }

  protected void hideKeyboard() {

    // Hide soft keyboard
    mSearchView.clearFocus();
    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    inputManager.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
  }

}
