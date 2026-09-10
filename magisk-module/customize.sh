SKIPUNZIP=0

# Ensure permissions on system files
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm_recursive $MODPATH/system/priv-app 0 0 0755 0644
set_perm $MODPATH/system/priv-app/AICallAgent/AICallAgent.apk 0 0 0644
set_perm $MODPATH/system/etc/permissions/privapp-permissions-aicallagent.xml 0 0 0644

ui_print "- AICallAgent Priv-App Module Installed"
ui_print "- Please reboot device to apply priv-app permissions"
