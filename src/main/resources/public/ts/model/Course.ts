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
    printcoursestodo : boolean;
    printcoursestocome : boolean;

    constructor() {
        this.allbyfolder = [];

        this.allCourses = [];
        this.coursesShared = [];
        this.coursesByUser = [];
        this.isSynchronized = false;
        this.getChoice();
    }
    toJsonForDelete(){
        return {
            coursesId: this.allCourses.filter(course => course.selectConfirm).map(course => course.courseid ),
        }
    }
    async coursesDelete() {
        let { coursesId } =  this.toJsonForDelete();
            try {
                console.log(coursesId[0])
                let courses = await http.delete(`/moodle/course/${coursesId[0]}`);
                this.allbyfolder = Mix.castArrayAs(Course, courses.data);
            } catch (e) {
                notify.error("Delete function didn't work");
            }
    }
    /*
    ___________________delete courses à tester______________
        try {
            await http.delete('/moodle/course', { data: this.toJsonForDelete() } );
        } catch (e) {
            notify.error("Delete function didn't work");
            throw e;
        }
    }
    */
    toJSON() {
        return {
            printcreatecoursesrecents : this.printcreatecoursesrecents,
            printcoursestodo : this.printcoursestodo,
            printcoursestocome : this.printcoursestocome
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
            /*this.allCourses.forEach(function(course) {
                course.date = new Date((new Date((course.date).toString())).toLocaleDateString());
            });*/
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

    async getChoice() {
            try {
                const {data} = await http.get('/moodle/choices');
                this.printcreatecoursesrecents = data[0].choice;
                this.printcoursestodo = data[1].choice;
                this.printcoursestocome = data[2].choice;
            } catch (e) {
                notify.error("Get Choice function didn't work");
                }
    }

    async setChoice(view:number){
        if (view == 1){
            try {
                await http.put(`/moodle/choices/lastCreation`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice lastCreation function didn't work");
            }
        }else if (view == 2){
            try {
                await http.put(`/moodle/choices/toDo`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice toDo function didn't work");
            }
        }else if (view ==3){
            try {
                await http.put(`/moodle/choices/toCome`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice toCome function didn't work");
            }
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