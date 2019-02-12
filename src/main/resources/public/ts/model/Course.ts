import { notify, model, } from 'entcore';
import http from 'axios';
import {Mix, Selectable, Selection} from 'entcore-toolkit';
import {Folder} from "./Folder";


export interface Course {
    _id : 91;
    fullname : string;
    summary : string;
    date : Date;
    author : Author;
    role : string;
    categoryid : number;
    imageurl : string;
    type : string;
    typeA : number;
}

export class Course {
    constructor(){
        this.type='1';
    }
    toJSON() {
        return {
            fullname : this.fullname,
            categoryid : 1,
            summary: this.summary,
            date : this.date,
            author : this.author,
            role : this.role,
            imageurl : "https://medias.liberation.fr/photo/552903--.jpg",
            type : this.type,
            typeA : this.typeA
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
            await http.delete(`/moodle/course/${this._id}`);
        } catch (e) {
            notify.error("Delete function didn't work");
        }
    }
}

export class Courses {
    allbyfolder: Course[];
    coursesShared: Course[];
    coursesByUser: Course[];
    constructor() {
        this.allbyfolder = [];
        this.coursesShared = [];
        this.coursesByUser = [];
    }
    async getCoursesbyFolder (folder_id: number) {
        try {
            let courses = await http.get(`/moodle/courses/${folder_id}`);
            this.allbyfolder = Mix.castArrayAs(Course, courses.data);
        } catch (e) {
            throw e;
        }
    }
    async getCoursesbyUser () {
        try {
            let courses = await http.get(`/moodle/users/courses`);
            this.coursesByUser = Mix.castArrayAs(Course, courses.data);
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