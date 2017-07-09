# PermissionTools
Detecting the Runtime Permissions since Android 6.0 Marshmallow version


How to use

1. MainActivity: 

	-> oncreate()

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPermissionUtils = new PermissionUtils(this, permissionListener);
        } else {
            //to-do sth
        }



	->onRequestPermissionsResult()
    
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mPermissionUtils != null) {
            mPermissionUtils.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }



	->onActivityResult()


        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mPermissionUtils != null) {
            mPermissionUtils.onActivityResult(requestCode, resultCode, data);
        }
        super.onActivityResult(requestCode, resultCode, data);
        }