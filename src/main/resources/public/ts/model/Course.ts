import {_, notify, Shareable, Rights} from "entcore";
import http from "axios";
import {Mix} from "entcore-toolkit";

export class Course implements Shareable{
    shared: any;
    owner: {userId: string; displayName: string};
    myRights: Rights<Course>;

    courseid : number;
    id:number;

    fullname : string;
    summary : string;
    date : Date;
    auteur : Author;
    role : string;
    startdate : Date;
    enddate : Date;
    categoryid : number;
    folderid : number;
    imageurl : string;
    type : string;
    typeA : string;
    select: boolean;
    selectConfirm: boolean;
    masked: boolean;
    favorites: boolean;

    constructor(){
        this.type = "1";
        this.select = false;
        this.selectConfirm = false;
        this.myRights = new Rights<Course>(this);
    }


    toJSON() {
        let tmpImgUrl = this.imageurl? this.imageurl.split("/") : [null];
        return {
            courseid: this.courseid,
            fullname: this.fullname,
            categoryid: 1,
            summary: this.summary,
            date: this.date,
            auteur: this.auteur,
            imageurl: tmpImgUrl[tmpImgUrl.length-1],
            type: this.type,
            typeA: this.typeA,
            folderid: this.folderid,
            id: this.courseid
        }
    }

    toJson() {
        return {
            role: "editingteacher"
        }
    }

    async share() {
        try {
            await http.put('/moodle/course', this.toJson());
        } catch (e) {
            notify.error("Share function didn't work");
            throw e;
        }
    }

    async create() {
        try {
            const {data} = await http.post('/moodle/course', this.toJSON());
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

    async getCoursePreferences() {
        if(this.courseid != null) {
            try {
                const {data} = await http.get(`/moodle/course/preferences/${this.courseid}`);
                if (data.length != 0) {
                    this.masked = data[0].masked;
                    this.favorites = data[0].favorites;
                } else {
                    this.masked = false;
                    this.favorites = false;
                }
            } catch (e) {
                notify.error("Get course preferences function didn't work");
                throw e;
            }
        }else{
            this.masked = false;
            this.favorites = false;
        }
    }
}

export class Courses {
    allbyfolder: Course[];
    coursesShared: Course[];
    coursesByUser: Course[];
    isSynchronized: Boolean;
    allCourses: Course[];
    printcourseslastcreation : boolean;
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
        try {
            await http.delete('/moodle/course', { data: this.toJsonForDelete() } );
        } catch (e) {
            notify.error("Delete function didn't work");
            throw e;
        }
    }

    toJSON() {
        return {
            printcourseslastcreation : this.printcourseslastcreation,
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
            _.each(this.allCourses,function(course) {
               course.id = course.courseid;
                course._id = course.courseid;
                course.owner = {userId: course.auteur[0].entidnumber, displayName:course.auteur[0].firstname+" "+ course.auteur[0].lastname};
            });
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
                if(data.length != 0) {
                    this.printcourseslastcreation = data[0].lastcreation;
                    this.printcoursestodo = data[0].todo;
                    this.printcoursestocome = data[0].tocome;
                }
                else
                {
                    this.printcourseslastcreation = true;
                    this.printcoursestodo = true;
                    this.printcoursestocome = true;
                }
            } catch (e) {
                notify.error("Get Choice function didn't work");
                }
    }

    async setChoice(view:number){
        if (view == 1){
            try {
                await http.put(`/moodle/choices/lastcreation`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice lastCreation function didn't work");
            }
        }else if (view == 2){
            try {
                await http.put(`/moodle/choices/todo`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice toDo function didn't work");
            }
        }else if (view ==3){
            try {
                await http.put(`/moodle/choices/tocome`, this.toJSON());
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