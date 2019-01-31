import { notify, model, } from 'entcore';
import http from 'axios';
import {Mix, Selectable, Selection} from 'entcore-toolkit';
import {Folder} from "./Folder";


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
    imageurl : string;
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
            imageurl : this.imageurl,
            firstname :this.firstname,
            lastname : this.lastname,
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
    allbyfolder: Course[];
    coursesShared: Course[];
    constructor() {
        this.allbyfolder = [];
        this.coursesShared = [];
    }
    async getCoursesbyFolder (folder_id: number) {
        try {
            let courses = await http.get(`/moodle/courses/${folder_id}`);
            this.allbyfolder = Mix.castArrayAs(Course, courses.data);
        } catch (e) {
            throw e;
        }
    }

    async getCoursesAndSheredbyFolder () {
        try {
            let courses = await http.get(`/moodle/users/coursesAndShared`);
            this.coursesShared = Mix.castArrayAs(Course, courses.data);
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