import { notify, model, } from 'entcore';
import http from 'axios';

export interface Course {
    username : string;
    idnumber : string;
    email : string;
    firstname : string;
    lastname : string;
    fullname : string;
    shortname : string;
    categoryid : number;
    // courseidnumber : number;
    // summary : string;
    // imagebase64 :

}

export class Course {
    
    toJSON() {
        return {
            username : model.me.username,
            idnumber : model.me.userId,
            firstname : model.me.firstName,
            lastname : model.me.lastName,
            fullname : this.fullname,
            shortname : this.shortname,
            categoryid : 1,
            // courseidnumber : this.courseidnumber,
            // summary : this.summary,
            // imagebase64 : this.
        }
    }

    async create() {
        try {
            await http.post('/moodle/course', this.toJSON());
        } catch (e) {
            notify.error("Save function didn't work");
            throw e;
        }
    }
    async delete() {
        try {
            await http.delete(`/moodle/course/1`);
        } catch (e) {
            notify.error("Save function didn't work");
            throw e;
        }
    }
}