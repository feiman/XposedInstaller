package de.robv.android.xposed.installer;

import java.util.Comparator;
import java.util.Set;

import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ModulesFragment extends ListFragment {
	private Set<String> enabledModules;
	private String installedXposedVersion;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
        
		installedXposedVersion = InstallerFragment.getJarInstalledVersion(null);
		
        ModuleAdapter modules = new ModuleAdapter(getActivity());
        enabledModules = PackageChangeReceiver.getEnabledModules(getActivity());
        
		PackageManager pm = getActivity().getPackageManager();
		for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
			if (app.metaData == null || !app.metaData.containsKey("xposedmodule"))
				continue;
			
			String minVersion = app.metaData.getString("xposedminversion");
			String description = app.metaData.getString("xposeddescription", "");
			modules.add(new XposedModule(app.packageName, pm.getApplicationLabel(app).toString(), pm.getApplicationIcon(app), minVersion, description));
		}
		
		modules.sort(new Comparator<XposedModule>() {
			@Override
			public int compare(XposedModule lhs, XposedModule rhs) {
				return lhs.appName.compareTo(rhs.appName);
			}
		});
        
        setListAdapter(modules);
        setEmptyText(getActivity().getString(R.string.no_xposed_modules_found));

        getListView().setFastScrollEnabled(true);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		CheckBox checkbox = (CheckBox)v.findViewById(R.id.checkbox);
		checkbox.toggle();
	}

    private class ModuleAdapter extends ArrayAdapter<XposedModule> {
		public ModuleAdapter(Context context) {
			super(context, R.layout.list_item_module, R.id.text);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
		 	View view = super.getView(position, convertView, parent);

			if (convertView == null) {
				// The reusable view was created for the first time, set up the listeners on the checkbox and image
				((CheckBox) view.findViewById(R.id.checkbox)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						String packageName = (String) buttonView.getTag();
						boolean changed = enabledModules.contains(packageName) ^ isChecked;
						if (changed) {
							synchronized (enabledModules) {
								if (isChecked)
									enabledModules.add(packageName);
								else
									enabledModules.remove(packageName);
							}

							PackageChangeReceiver.setEnabledModules(getContext(), enabledModules);
							PackageChangeReceiver.updateModulesList(getContext(), enabledModules);
						}
					}
				});

				((ImageView) view.findViewById(R.id.icon)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String packageName = (String) v.getTag();
						Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
						if (launchIntent != null)
							startActivity(launchIntent);
						else
							Toast.makeText(getActivity(), getActivity().getString(R.string.module_no_ui), Toast.LENGTH_LONG).show();
					}
				});
			}

			XposedModule item = getItem(position);
			((CheckBox) view.findViewById(R.id.checkbox)).setTag(item.packageName);
			((ImageView) view.findViewById(R.id.icon)).setTag(item.packageName);

			((ImageView) view.findViewById(R.id.icon)).setImageDrawable(item.icon);

			TextView descriptionText = (TextView) view.findViewById(R.id.description);
			if (item.description.length() > 0)
				descriptionText.setText(item.description);
			else
				descriptionText.setText(getActivity().getString(R.string.module_empty_description));

			CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
			checkbox.setChecked(enabledModules.contains(item.packageName));
			TextView warningText = (TextView) view.findViewById(R.id.warning);

			if (item.minVersion == null) {
				checkbox.setEnabled(false);
				descriptionText.setVisibility(View.GONE);
				warningText.setText(getString(R.string.no_min_version_specified));
				warningText.setVisibility(View.VISIBLE);
            } else if (installedXposedVersion != null && PackageChangeReceiver.compareVersions(item.minVersion, installedXposedVersion) > 0) {
            	checkbox.setEnabled(false);
				descriptionText.setVisibility(View.GONE);
            	warningText.setText(String.format(getString(R.string.warning_xposed_min_version), 
            			PackageChangeReceiver.trimVersion(item.minVersion)));
            	warningText.setVisibility(View.VISIBLE);
            } else if (PackageChangeReceiver.compareVersions(item.minVersion, PackageChangeReceiver.MIN_MODULE_VERSION) < 0) {
            	checkbox.setEnabled(false);
				descriptionText.setVisibility(View.GONE);
            	warningText.setText(String.format(getString(R.string.warning_min_version_too_low), 
            			PackageChangeReceiver.trimVersion(item.minVersion), PackageChangeReceiver.MIN_MODULE_VERSION));
            	warningText.setVisibility(View.VISIBLE);
            } else {
            	checkbox.setEnabled(true);
				descriptionText.setVisibility(View.VISIBLE);
            	warningText.setVisibility(View.GONE);
            }
            return view;
		}
    	
	}

	private static class XposedModule {
		String packageName;
		String appName;
		Drawable icon;
		String minVersion;
		String description;

		public XposedModule(String packageName, String appName, Drawable icon, String minVersion, String description) {
			this.packageName = packageName;
			this.appName = appName;
			this.icon = icon;
			this.minVersion = minVersion;
			this.description = description.trim();
		}

		@Override
		public String toString() {
			return appName;
		}
	}
}
