import { notify } from 'entcore';
import http from 'axios';

export interface Course {
    id_moodle : number;
    title : string;
    description : string;
    userId : number;
    firstName : string;
    lastName : string;
}

export class Course {
    
    toJSON() {
        return {
            id_moodle : this.id_moodle,
            title : this.title,
            description : this.description,
            userId : this.userId,
            firstName : this.firstName,
            lastName : this.lastName
        }
    }

    async create() {
        try {
            await http.post('/moodle/courses', this.toJSON());
        } catch (e) {
            notify.error("Save function didn't work");
            throw e;
        }
    }
}