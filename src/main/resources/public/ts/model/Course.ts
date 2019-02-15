import {notify,} from 'entcore';
import http from 'axios';
import {Mix} from 'entcore-toolkit';


export interface Course {
    courseid : number;
    fullname : string;
    summary : string;
    date : Date;
    author : Author;
    role : string;
    categoryid : number;
    folderid : number;
    imageurl : string;
    type : string;
    typeA : string;
}

export class Course {
    constructor(){
        this.type = "1";
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
            const { data } = await http.post('/moodle/course', this.toJSON());
            this.courseid = data.id;
            this.goTo('edit');
        } catch (e) {
            notify.error("Save function didn't work");
            throw e;
        }
    }
    async delete() {
        try {
            await http.delete(`/moodle/course/${this.courseid}`);
        } catch (e) {
            notify.error("Delete function didn't work");
        }
    }
    async goTo(scope: string = 'view') {
        window.open(`/moodle/course/${this.courseid}?scope=${scope}`);
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