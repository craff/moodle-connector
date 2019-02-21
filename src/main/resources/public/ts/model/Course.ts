import {_, notify,} from 'entcore';
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
    select: boolean;
    selectConfirm: boolean;
}

export class Course {
    constructor(){
        this.type = "1";
        this.select = false;
        this.selectConfirm = false;
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
            typeA : this.typeA,
            folderid : this.folderid
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
    isSynchronized: Boolean;
    allCourses: Course[];
    printcreatecoursesrecents : boolean;

    constructor() {
        this.allbyfolder = [];

        this.allCourses = [];
        this.coursesShared = [];
        this.coursesByUser = [];
        this.isSynchronized = false;
        this.getChoice();
    }
    toJSON() {
        return {
            printcreatecoursesrecents : this.printcreatecoursesrecents
        }
    }
    /*async getCoursesbyFolder (folder_id: number) {
        try {
            let courses = await http.get(`/moodle/courses/${folder_id}`);
            this.allbyfolder = Mix.castArrayAs(Course, courses.data);
        } catch (e) {
            throw e;
        }
    }*/

    async getCoursesbyUser (userId: string) {
        try {
            let courses = await http.get(`/moodle/users/courses`);
            let allCourses = Mix.castArrayAs(Course, courses.data);

            this.allCourses = allCourses;
            this.coursesByUser = _.filter(allCourses, function(cours) { return cours.auteur[0].entidnumber === userId; });
            this.coursesShared = _.filter(allCourses, function(cours) { return cours.auteur[0].entidnumber !== userId; });
            this.coursesByUser = this.coursesByUser.sort(
                function compare(a, b) {
                    if (a.date < b.date)
                        return 1;
                    if (a.date > b.date)
                        return -1;
                }
            );

            this.isSynchronized = true;
        } catch (e) {
            this.isSynchronized = false;
            throw e;
        }
    }
    /*async getCoursesAndSheredbyFolder () {
        try {
            let courses = await http.get(`/moodle/users/coursesAndShared`);
            this.coursesShared = Mix.castArrayAs(Course, courses.data);
        } catch (e) {
            throw e;
        }
    }*/

    async getChoice(){
        try {
            let {data} = await http.get('/moodle/choices/lastCreation');
            console.log(data);
            this.printcreatecoursesrecents = data.choice;
        } catch (e) {
            notify.error("Get Choice function didn't work");
        }
    }

    async setChoice(){
        await http.put(`/moodle/choices/lastCreation`, this.toJSON());
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