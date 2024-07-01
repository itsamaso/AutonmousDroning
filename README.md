<h1 align="center"> Mini Drone Simulation </h1>

___________________________________________________________________________________________________________________________________________________________

<h2 align="center"> <strong>Table Of Contents</strong></h2>

___________________________________________________________________________________________________________________________________________________________

<p align="center">
  <a href="#introduction">Introduction</a> <br> 
  <a href="#features">Features</a> <br>
  <a href="#waypoint">Waypoint</a> <br>
  <a href="#how-does-the-drone-fly-autonomously-using-virtual-stick">How Does The Drone Fly Autonomously Using Virtual Stick</a> <br>
  <a href="#prerequisites">Prerequisites</a> <br>
  <a href="#how-to-run">How To Run</a> <br>
  <a href="#closing">Closing</a> <br>
</p>

___________________________________________________________________________________________________________________________________________________________


<h2>Introduction</h2>

___________________________________________________________________________________________________________________________________________________________

_Welcome to the Mini Drone Simulation project, this repository is dedicated to the development and simulation of an autonomous mini drone. The primary goal of this project is to create a comprehensive environment where the drone can navigate autonomously using a variety of control algorithms._

_The project is applied on **`Mavic Mini 1`**, which originally does not support autonomous flights._

___________________________________________________________________________________________________________________________________________________________


<h2>Features</h2>

___________________________________________________________________________________________________________________________________________________________

- _**Autonomously** navigating the drone towards destinations that are given before taking off, respectively._
- _Freely choose which destination to navigate to - among all existing destinations._
- _Dynamically set an existing destination (by dragging) to a new one during the drone's flight and navigates to it._
  
  _The algorithmic logic behind this feature is as follows:_

  ```java
  onMarkerDragStart(@NonNull Marker marker)
  ```
  
    > **_Once dragging starts, it pauses the drone's current mission._**
    
  ```java
  onMarkerDrag(@NonNull Marker marker)
  ```
  
    > **_Continuously updates the current coordinate as long as the location marker (in Waypoint) is being dragged._**
    
  ```java
  onMarkerDragEnd(@NonNull Marker marker)
  ```
    
    > **_Once there is no longer dragging, updates the current coordinate to be the new marker's coordinate after dragging and resumes mission._**

  _Where all 3 methods are integrated in the following **`MoveMarkerInRealTime()`**:_

  ```java
  private void MoveMarkerInRealTime(){
        Mymap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {
                getWaypointMissionOperator().PauseMission();

            }

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {
                LocationCoordinate2D newCoordinate=new LocationCoordinate2D(marker.getPosition().latitude,marker.getPosition().longitude);
                getWaypointMissionOperator().updateWaypoint(newCoordinate);

            }

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                LocationCoordinate2D newCoordinate = new LocationCoordinate2D(marker.getPosition().latitude, marker.getPosition().longitude);
                getWaypointMissionOperator().updateWaypoint(newCoordinate);
                getWaypointMissionOperator().resumeMission();

            }
        });
    }
  ```

___________________________________________________________________________________________________________________________________________________________

<h2>Waypoint</h2>

___________________________________________________________________________________________________________________________________________________________

_Waypoint is an application that sets up the drone's flight before taking off, using various buttons where each one serves its own purpose.
Essentially, the feature where you can dynamically set a new destination, is highly relied on the Waypoint._

<p align="center">
  <img src="WaypointSample.jpg" width="360" height="800"/>
</p>

How buttons work:

- `LOCATE` _Zooms in towards the red airplane displayed in the app (which is the drone' current position)._
- `ADD` _Adds a new destination by just clicking - in order to perform the click, the 'Add' button must be clicked_
- `CLEAR` _Clears all currently displayed destinations._
- `ADD POINT` _Adds a new destination by providing latitude, longtitude and altitude._
- `CONFIG` _Configures/initializes the needed specifications for the flight, such as: speed, altitude, where-to-land... etc._
- `UPLOAD` _Uploads all filled settings for the Waypoint's mission so it can be ready to take off._
- `START` _Starts the mission, after uploading all needed settings._
- `STOP` _Stops the mission._

Please note, after starting your mission, during the mission/flight, you will be able to dynamically set a new destination.

Here is a video example of how Waypoint behaves: **LINK HERE**

In order to review the class that is responsible for the Waypoint, please take a look at `MavicMiniWaypoint.java`

___________________________________________________________________________________________________________________________________________________________


<h2>How Does The Drone Fly Autonomously Using Virtual Stick</h2>

___________________________________________________________________________________________________________________________________________________________

Essentially, **`MavicMiniMissionOperator.java`** is responsible to provide the autonomous logic by implementing the following methodology:

  ```java
  private Observer<LocationCoordinate3D> locationObserver = new Observer<LocationCoordinate3D>() {
        @Override
        public void onChanged(LocationCoordinate3D currentLocation) {
            // Observing changes to the drone's location coordinates
            state = WaypointMissionState.EXECUTING;

            distanceToWaypoint = distanceInMeters(
                    new LocationCoordinate2D(currentWaypoint.coordinate.getLatitude(), currentWaypoint.coordinate.getLongitude()),
                    new LocationCoordinate2D(currentLocation.getLatitude(), currentLocation.getLongitude())
            );

            double longitudeDiff = currentWaypoint.coordinate.getLongitude() - currentLocation.getLongitude();
            double latitudeDiff = currentWaypoint.coordinate.getLatitude() - currentLocation.getLatitude();
            if(!isPausedForUpdate){
                pauseMission();
            }

            if (Math.abs(latitudeDiff) > originalLatitudeDiff) {
                originalLatitudeDiff = Math.abs(latitudeDiff);
            }

            if (Math.abs(longitudeDiff) > originalLongitudeDiff) {
                originalLongitudeDiff = Math.abs(longitudeDiff);
            }

            // Terminating the sendDataTimer and creating a new one
            sendDataTimer.cancel();
            sendDataTimer = new Timer();

            if (!travelledLongitude) {
                float speed = Math.max(
                        (float) (mission.getAutoFlightSpeed() * (Math.abs(longitudeDiff) / originalLongitudeDiff)),
                        0.5f
                );

                directions.pitch = longitudeDiff > 0 ? speed : -speed;
            }

            if (!travelledLatitude) {
                float speed = Math.max(
                        (float) (mission.getAutoFlightSpeed() * (Math.abs(latitudeDiff) / originalLatitudeDiff)),
                        0.5f
                );

                directions.roll = latitudeDiff > 0 ? speed : -speed;
            }

            // When the longitude difference becomes insignificant
            if (Math.abs(longitudeDiff) < 0.000002) {
                checkLong++;
                if(checkLong==1) {
                    textAppenderOper.appendTextAndScroll("finished travelling LONGITUDE");
                    Log.d(TAG, "finished travelling LONGITUDE");
                }
                directions.pitch = 0f;
                travelledLongitude = true;
            }

            if (Math.abs(latitudeDiff) < 0.000002) {
                CheckLat++;
                if(CheckLat==1) {
                    textAppenderOper.appendTextAndScroll("finished travelling LATITUDE");
                    Log.d(TAG, "finished travelling LATITUDE");
                }
                directions.roll = 0f;
                travelledLatitude = true;
            }

            // When the latitude difference becomes insignificant and there is no longitude difference
            if (travelledLatitude && travelledLongitude) {
                waypointTracker++;
                if (waypointTracker < waypoints.size()) {
                    currentWaypoint = waypoints.get(waypointTracker);
                    originalLatitudeDiff = -1.0;
                    originalLongitudeDiff = -1.0;
                    travelledLongitude = false;
                    travelledLatitude = false;
                    directions = new Direction(); // Assuming Direction is a class with an appropriate constructor
                } else {
                    state = WaypointMissionState.EXECUTION_STOPPING;
                    if (operatorListener != null) {
                        operatorListener.onExecutionFinish(null);
                    }
                    stopMission(null);
                    isLanding = true;
                    sendDataTimer.cancel();
                    if (isLanding && currentLocation.getAltitude() == 0f && !isLanded) {
                        sendDataTimer.cancel();
                        isLanded = true;
                    }

                    removeObserver();
                }
                sendDataTimer.cancel(); // Cancel all scheduled data tasks
            } else {
                if (state == WaypointMissionState.EXECUTING) {
                    directions.altitude = currentWaypoint.altitude;
                } else if (state == WaypointMissionState.EXECUTION_PAUSED) {
                    directions = new Direction(0f, 0f, 0f, currentWaypoint.altitude);
                }
                move(directions);
            }
            if(isPausedForUpdate){
                resumeMission();
            }
        }
    };
  ```

  The above code is pretty much the autonomous behavior being tackled in the project, where it can be executed in `executeMission()`.

___________________________________________________________________________________________________________________________________________________________


<h2>Prerequisites</h2>

___________________________________________________________________________________________________________________________________________________________

- **`DJI SDK MOBILE 4.16.4`**
- **`Google Maps.`**

___________________________________________________________________________________________________________________________________________________________


<h2>How To Run</h2>

___________________________________________________________________________________________________________________________________________________________

- _Open a new project in **Android Studio**._
- _Perform the command line **`git clone https://github.com/osamaghaliah/Mini-Drone-Simulation.git`** in your terminal_
- _Connect your phone to the **Android Studio**._
- _Run the code._

___________________________________________________________________________________________________________________________________________________________


<h2>Closing</h2>

___________________________________________________________________________________________________________________________________________________________

_```To be continued...```_
