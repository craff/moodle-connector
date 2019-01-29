import { notify, model, } from 'entcore';
import http from 'axios';
import {Mix, Selectable, Selection} from 'entcore-toolkit';


export interface Course {
    id? : 91;
    username : string;
    idnumber : string;
    email : string;
    firstname : string;
    lastname : string;
    fullname : string;
    courseid : number;
    summary : string;
    date : Date;
    author : Author;
    role : string;
    categoryid : number;
    // imagebase64 : string;
}

export class Course {
    
    toJSON() {
        return {
            courseid : this.courseid,
            fullname : this.fullname,
            categoryid : 1,
            summary: this.summary,
            date : this.date,
            author : this.author,
            role : this.role,
            // imagebase64 : this.imagebase64
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
            await http.delete(`/moodle/course/${this.id}`);
        } catch (e) {
            notify.error("Delete function didn't work");
        }
    }
}


export class Courses {
    all: Course[];

    constructor() {
        this.all = [];
    }
    async sync (folder_id: number) {
        try {
            let courses = await http.get(`/moodle/coursesAndshared/${folder_id}`);
            this.all = Mix.castArrayAs(Course, courses.data);
        } catch (e) {
            throw e;
        }
    }
}

export class Author{
    entidnumber : string;
    firstname : string;
    lastname : string;

    toJson() {
        return {
            entidnumber : this.entidnumber,
            firstname : this.firstname,
            lastname : this.lastname,
        }
    }
}