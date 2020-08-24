
export class Utils {
    static  PRINCIPAL_FOLDER_TYPE = {
        my_courses : 'MY_COURSES',
        shared : 'SHARED',
        published : 'PUBLISHED'
    };

    static is_my_course_category_selected  (principal_folder):boolean  {
        return principal_folder === this.PRINCIPAL_FOLDER_TYPE.my_courses
    };
    static is_shared_category_selected  (principal_folder):boolean {
        return principal_folder === this.PRINCIPAL_FOLDER_TYPE.shared
    };
    static is_published_category_selected (principal_folder):boolean  {
        return principal_folder === this.PRINCIPAL_FOLDER_TYPE.published
    };
    static safeApply (that: any) {
        return new Promise((resolve) => {
            let phase = (that.$root !== null)?that.$root.$$phase : undefined;
            if(phase === '$apply' || phase === '$digest') {
                if(resolve && (typeof(resolve) === 'function')) resolve();
            } else {
                if (resolve && (typeof(resolve) === 'function')) that.$apply(resolve);
                else that.$apply();
            }
        });
    }
}