package com.dji.sdk.sample.demo.lookat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.sdk.sample.R;

import java.util.ArrayList;
import java.util.List;

import dji.common.mission.waypoint.Waypoint;

public class ChooseYourPoint {
    private List<Waypoint> new_waypoint_list,Waypoint;
    private Context myContext;
    private OnPointSelectedListener pointSelectedListener;
    private boolean isDialogShowing = false;


    public ChooseYourPoint(Context context, List<Waypoint> original_WayPoint_List, OnPointSelectedListener listener) {
        this.Waypoint=original_WayPoint_List;
        this.new_waypoint_list = original_WayPoint_List;
        this.myContext = context;
        this.pointSelectedListener = listener;
    }

    public void showSettingsDialogChooseYourPoint() {
        if (isDialogShowing) {
            return;
        }

        isDialogShowing = true;

        LinearLayout Setting = (LinearLayout) LayoutInflater.from(this.myContext)
                .inflate(R.layout.dialog_choose_your_point, null);
        TextView point_number_text = (TextView) Setting.findViewById(R.id.point_number);
        new AlertDialog.Builder(myContext)
                .setTitle("Choose Your Point")
                .setView(Setting)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        String string_number = point_number_text.getText().toString();
                        int point_N = 0;
                        try {
                            point_N = Integer.parseInt(nullToIntegerDefault(string_number));
                        } catch (NumberFormatException e) {
                            Toast.makeText(myContext, "Invalid input. Please enter a valid number.", Toast.LENGTH_SHORT).show();
                            isDialogShowing = false;
                            return;
                        }

                        if (point_N < 0 || point_N >= new_waypoint_list.size()) {
                            Toast.makeText(myContext, "Invalid point number. Please enter a number between 0 and " + (new_waypoint_list.size() - 1), Toast.LENGTH_SHORT).show();
                            isDialogShowing = false;
                            return;
                        }

                        UpdateWayPointList(point_N);
                        if (pointSelectedListener != null) {
                            pointSelectedListener.onPointSelected(point_N);
                        }
                        isDialogShowing = false;
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        isDialogShowing = false;
                    }
                })
                .create()
                .show();
    }

    private void UpdateWayPointList(int pointNumber) {
        Waypoint temp = Waypoint.get(pointNumber);
        List<Waypoint> temp_List = new ArrayList<>();
        temp_List.add(temp);
        for (int i = 0; i < Waypoint.size(); i++) {
            if (i != pointNumber) {
                temp_List.add(Waypoint.get(i));
            }
        }
        setListWaypoint(temp_List);
    }

    public void setListWaypoint(List<Waypoint> L) {
        new_waypoint_list.clear();
        new_waypoint_list.addAll(L);
    }

    public List<Waypoint> getNew_waypoint_list() {
        return new_waypoint_list;
    }

    public String nullToIntegerDefault(String value) {
        String newValue = value;
        if (!isIntValue(newValue)) newValue = "0";
        return newValue;
    }

    private boolean isIntValue(String value) {
        try {
            String newValue = value.replace(" ", "");
            Integer.parseInt(newValue);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public interface OnPointSelectedListener {
        void onPointSelected(int pointNumber);
    }
}
