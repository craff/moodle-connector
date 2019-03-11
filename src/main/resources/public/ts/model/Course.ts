import {_, notify, Rights, Shareable} from "entcore";
import http from "axios";
import {Mix} from "entcore-toolkit";

export class Course implements Shareable{
    shared : any;
    owner : {userId: string; displayName: string};
    myRights : Rights<Course>;

    courseid : number;
    id : number;

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
    select : boolean;
    selectConfirm : boolean;
    masked : boolean;
    favorites : boolean;
    infoImg: {
        name: string;
        type: string;
        compatibleMoodle: boolean;
    };

    constructor(){
        this.type = "1";
        this.select = false;
        this.selectConfirm = false;
        this.myRights = new Rights<Course>(this);
    }


    toJson() {
        return {
            role: "edit"
        }
    }

    toJsonPreferences() {
        return {
            courseid: this.courseid,
            masked: this.masked,
            favorites: this.favorites
        }
    }

    async share() {
        try {
            await http.put(`/moodle/share/resource/${this.courseid}`);
        } catch (e) {
            notify.error("Share function didn't work");
            throw e;
        }
    }

    async setInfoImg() {
        const typesImgNoSend = ["image/png", "image/jpg", "image/jpeg", "image/gif"];
        try {
            const {data: {metadata}} = await http.get(`/moodle/info/image/${this.imageurl ? this.imageurl.split("/").slice(-1)[0] : null}`);
            this.infoImg = {
                name: metadata.filename,
                type: metadata["content-type"],
                compatibleMoodle: !typesImgNoSend.some(type => type === metadata["content-type"]),
            };
        } catch (e) {
            notify.error("info img function didn't work");
            throw e;
        }
    }

    toJSON() {
        return {
            courseid: this.courseid,
            fullname: this.fullname,
            categoryid: 1,
            summary: this.summary,
            date: this.date,
            auteur: this.auteur,
            imageurl: this.imageurl ? this.imageurl.split("/").slice(-1)[0] : null,
            type: this.type,
            typeA: this.typeA,
            folderid: this.folderid,
            id: this.courseid,
            nameImgUrl: this.infoImg ? this.infoImg.name.toLocaleLowerCase() : null,
        }
    }

    async create() {
        try {
            const {data} = await http.post('/moodle/course', this.toJSON());
            this.courseid = data.id;
            this.infoImg.compatibleMoodle = false;
            this.goTo('edit');
        } catch (e) {
            notify.error("Save function didn't work");
            throw e;
        }
    }

    async goTo(scope: string = 'view') {
        window.open(`/moodle/course/${this.courseid}?scope=${scope}`);
    }

    async setPreferences(preference : string) {

        if(preference == "masked") {
            this.masked = !this.masked;
        }else if(preference == "favorites") {
            this.favorites = !this.favorites;
        }
        try {
            await http.put('/moodle/course/preferences', this.toJsonPreferences());
        } catch (e) {
            notify.error("Set course preference function didn't work");
            throw e;
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
    coursesToDo : Course[];
    coursesToCome : Course[];

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
            console.log(allCourses);

            this.allCourses = allCourses;
            _.each(this.allCourses,function(course) {
                course.id = course.courseid;
                course._id = course.courseid;
                course.owner = {userId: course.auteur[0].entidnumber, displayName:course.auteur[0].firstname+" "+ course.auteur[0].lastname};
            });
            this.coursesByUser = _.filter(this.allCourses, function(cours) { return cours.auteur[0].entidnumber === userId; });
            this.coursesShared = _.filter(this.allCourses, function(cours) { return cours.auteur[0].entidnumber !== userId; });
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

    coursesToShow(id : string){
        if(id == "all"){
            return _.filter(this.coursesByUser, function(cours) { return !(cours.masked); });
        }else if (id =="doing"){
            return _.filter(this.coursesByUser, function(cours) { return !(cours.masked); });
        }else if (id == "favorites"){
            return _.filter(this.coursesByUser, function(cours) { return cours.favorites; });
        }else if (id == "finished"){
            return _.filter(this.coursesByUser, function(cours) { return !(cours.masked); });
        }else if (id == "masked"){
            return _.filter(this.coursesByUser, function(cours) { return cours.masked; });
        }
    }

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