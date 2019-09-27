import {_, idiom, moment, notify, Rights, Shareable, } from "entcore";
import http from "axios";
import {Mix} from 'entcore-toolkit";
import {Folders} from "./Folder";

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
    timemodified : number;
    enrolmentdate : number;
    categoryid : number;
    folderid : number;
    imageurl : string;
    type : string;
    typeA : string;
    progress : string;
    select : boolean;
    selectConfirm : boolean;
    masked : boolean;
    favorites : boolean;
    infoImg : {
        name : string;
        type : string;
        compatibleMoodle : boolean;
    };
    duplication : string;
    usernumber : number;

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
            this.duplication = "non";
            this.goTo('view');
        } catch (e) {
            this.fullname = undefined;
            this.summary = undefined;
            throw e;
        }
    }

    async deleteDuplication() {
        try{
            await http.delete(`/moodle/courseDuplicate/${this.courseid}`);
        }catch(e){
            notify.error("delete failed duplicate course didn't work");
            throw e;
        }
    }

    async goTo(scope: string = 'view') {
        if(this.duplication == "non")
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
    allbyfolder : Course[];
    coursesShared : Course[];
    coursesSharedToFollow : Course[];
    coursesByUser : Course[];
    isSynchronized : Boolean;
    allCourses : Course[];
    lastcreation : boolean;
    todo : boolean;
    tocome : boolean;
    coursestodosort : any;
    coursestocomesort : any;
    coursesToDo : Course[];
    coursesToCome : Course[];
    coursesMyCourse : Course[];
    showCourses : Course[];
    folderid : number;
    searchInput : any;
    order : any;
    typeShow : any;

    constructor() {
        this.allbyfolder = [];
        this.allCourses = [];
        this.coursesShared = [];
        this.coursesSharedToFollow = [];
        this.coursesByUser = [];
        this.showCourses = [];
        this.isSynchronized = false;
        this.searchInput = {
            toDo: "",
            toCome: "",
            MyCourse: ""
        };
        this.order = {
            field: "modificationDate",
            desc: false
        };
        this.typeShow = [
            {id: 'all', name: 'Tout'},
            {id: 'doing', name: 'En cours'},
            {id: 'favorites', name: 'Favoris'},
            {id: 'finished', name: 'Terminés'},
            {id: 'masked', name: 'Masqués'}
        ];
        this.getChoice();
    }

    toJsonForDelete(){
        return {
            coursesId: this.allCourses.filter(course => course.selectConfirm).map(course => course.courseid )
        }
    }

    async coursesDelete() {
        try {
            await http.delete('/moodle/course', { data: this.toJsonForDelete() } );
        } catch (e) {
            notify.error(idiom.translate("moodle.error.delete.course"));
            throw e;
        }
    }

    toJsonForDuplicate(){
        return {
            coursesId: this.allCourses.filter(course => course.selectConfirm).map(course => course.courseid ),
            folderId: this.folderid
        }
    }

    async coursesDuplicate() {
        try {
            await http.post('/moodle/course/duplicate', this.toJsonForDuplicate());
        } catch (e) {
            notify.error("Duplicate function didn't work");
            throw e;
        }
    }

    async getDuplicateCourse () {
        try {
            let courses = await http.get(`/moodle/duplicateCourses`);
            return courses.data;
        } catch (e) {
            throw e;
        }
    }

    toJSON() {
        return {
            lastcreation : this.lastcreation,
            todo : this.todo,
            tocome : this.tocome,
            coursestodosort : this.coursestodosort[0].id,
            coursestocomesort : this.coursestocomesort[0].id
        }
    }

    async getCoursesbyUser (userId: string) {
        try {
            let courses = await http.get(`/moodle/users/courses`);
            let allCourses = Mix.castArrayAs(Course, courses.data.allCourses);
            this.allCourses = allCourses;
            let idAuditeur = courses.data.idAuditeur + "";
            let idEditingTeacher = courses.data.idEditingTeacher + "";
            let idStudent = courses.data.idStudent + "";

            _.each(this.allCourses,function(course) {
                course.id = course.courseid;
                course._id = course.courseid;
                course.owner = {userId: course.auteur[0].entidnumber, displayName:course.auteur[0].firstname + " " + course.auteur[0].lastname};
                if(course.summary == null) {
                    course.summary = "";
                } else {
                    course.summary = course.summary.replace(/<\/p><p>/g, " ; ").replace(/<p>/g, "").replace(/<\/p>/g, "");
                }
            });
            this.coursesByUser = _.filter(this.allCourses, function(cours) { return cours.role === idAuditeur; });
            this.coursesShared = _.filter(this.allCourses, function (cours) {
                return cours.role === idEditingTeacher && cours.auteur[0].entidnumber !== userId;
            });
            this.coursesSharedToFollow = _.filter(this.allCourses, function (cours) {
                return cours.role === idStudent && cours.auteur[0].entidnumber !== userId;
            });
            this.coursesSharedToFollow = this.coursesSharedToFollow.sort(
                function compare(a, b) {
                    if (a.enrolmentdate < b.enrolmentdate)
                        return 1;
                    if (a.enrolmentdate > b.enrolmentdate)
                        return -1;
                }
            );

            let now = moment();
            this.coursesToCome = _.filter(this.coursesSharedToFollow, function (course) {
                let coursDate = moment(course.startdate + "000", "x");
                return now.isBefore(coursDate);
            });

            this.coursesToDo = _.filter(this.coursesSharedToFollow, function (course) {
                let coursDate = moment(course.startdate + "000", "x");
                return coursDate.isBefore(now);
            });

            this.coursesMyCourse = _.filter(this.coursesByUser, function (course) {
                let coursDate = moment(course.startdate + "000", "x");
                return coursDate.isBefore(now);
            });

            this.isSynchronized = true;
        } catch (e) {
            this.isSynchronized = false;
            throw e;
        }
    }

    async getCoursesAndSharedByFolder () {
        try {
            let courses = await http.get(`/moodle/users/coursesAndShared`);
            this.coursesShared = Mix.castArrayAs(Course, courses.data);
        } catch (e) {
            throw e;
        }
    }

    lastCreation(){
        return this.coursesByUser.filter(course => course.duplication == 'non').slice(0,5);
    }

    isTheFirst(courseFirst : Course){
        if(this.coursesToShow("coursesToDo").indexOf(courseFirst) == 0 || $(window).width() < 800)
            return true;
        else
            return false;
    }

    coursesToShow(place : string){
        if(place == "coursesToDo"){
            if(this.coursesToDo) {
                this.showCourses = this.coursesToDo.filter(course => this.searchCoursesToDo(course));
                var id = this.coursestodosort[0].id;
            }
        }else if (place == "coursesToCome"){
            if(this.coursesToCome) {
                this.showCourses = this.coursesToCome.filter(course => this.searchCoursesToCome(course));
                var id = this.coursestocomesort[0].id;
            }
        }else if (place == "coursesMyCourse"){
            if(this.coursesMyCourse) {
                this.showCourses = this.coursesByUser.filter(course => this.searchMyCourses(course));
            }
        }
        if (id =="all")
            this.showCourses = _.filter(this.showCourses);
        else if (id == "doing")
            this.showCourses = _.filter(this.showCourses, function(cours) { return (cours.progress != "100%" && !(cours.masked)) });
        else if (id == "favorites")
            this.showCourses = _.filter(this.showCourses, function(cours) { return (cours.favorites); });
        else if (id == "finished")
            this.showCourses = _.filter(this.showCourses, function(cours) { return (cours.progress == "100%" && !(cours.masked)); });
        else if (id == "masked")
            this.showCourses = _.filter(this.showCourses, function(cours) { return cours.masked; });

        return this.showCourses;
    }

    orderCourses(coursesToOrder : Course[]) {
        return coursesToOrder.sort(
            (a, b) => {
                if(this.order.field == "creationDate") {
                    if (a.date > b.date)
                        if(this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.date < b.date)
                        if(this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if(this.order.field == "modificationDate") {
                    if (a.timemodified > b.timemodified)
                        if(this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.timemodified < b.timemodified)
                        if(this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if(this.order.field == "name") {
                    if (a.fullname.toLowerCase() < b.fullname.toLowerCase())
                        if(this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.fullname.toLowerCase() > b.fullname.toLowerCase())
                        if(this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if(this.order.field == "numberEnrolment") {
                    if (a.usernumber < b.usernumber)
                        if(this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.usernumber > b.usernumber)
                        if(this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if(this.order.field == "achievment") {
                    if (a.progress.slice(0,a.progress.length-1) < b.progress.slice(0,b.progress.length-1))
                        if(this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.progress.slice(0,a.progress.length-1) > b.progress.slice(0,b.progress.length-1))
                        if(this.order.desc)
                            return -1;
                        else
                            return 1;
                }
            }
        );
    }

    /**
     * search with teacher name
     */
    searchCoursesToDo = (item: Course) => {
        return !this.searchInput.toDo || idiom.removeAccents(item.auteur[0].lastname.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.toDo).toLowerCase()) !== -1 || idiom.removeAccents(item.fullname.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.toDo).toLowerCase()) !== -1 || idiom.removeAccents(item.summary.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.toDo).toLowerCase()) !== -1;
    };

    searchMyCourses = (item: Course) => {
        return !this.searchInput.MyCourse || idiom.removeAccents(item.fullname.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.MyCourse).toLowerCase()) !== -1 || idiom.removeAccents(item.summary.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.MyCourse).toLowerCase()) !== -1;
    };

    searchCoursesToCome = (item: Course) => {
        return !this.searchInput.toCome || idiom.removeAccents(item.auteur[0].lastname.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.toCome).toLowerCase()) !== -1 || idiom.removeAccents(item.fullname.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.toCome).toLowerCase()) !== -1 || idiom.removeAccents(item.summary.toLowerCase()).indexOf(
            idiom.removeAccents(this.searchInput.toCome).toLowerCase()) !== -1;
    };

    courseInFolderSearch (folders : Folders, currentFolder) {
        let folderToSearch = folders.listOfSubfolders.find(folder => folder.id == currentFolder);
        let subFoldersSearchForCourse = [];
        for (let i = 0; i < folderToSearch.subFolders.length; i++) {
            subFoldersSearchForCourse.push(folderToSearch.subFolders[i]);
            if (folderToSearch.subFolders[i].subFolders.length != 0) {
                subFoldersSearchForCourse.push(...this.courseInFolderSearch(folders, folderToSearch.subFolders[i].id));
            }
        }
        return subFoldersSearchForCourse;
    }

    getCourseInFolder(coursesToPrint : Course[], currentFolder, searching : string, folders : Folders) {
        let folderToSearch = folders.listOfSubfolders.find(folder => folder.id == currentFolder);
        if (folderToSearch.id == 0) {
            return _.filter(coursesToPrint, function (course) {
                return !!searching ? (course.fullname.toLowerCase().includes(searching.toLowerCase()) ||
                    course.summary.toLowerCase().includes(searching.toLowerCase())) : course.folderid == 0;
            });
        } else {
            if (searching == '') {
                return _.filter(coursesToPrint, function (course) {
                    return (course.folderid == folderToSearch.id)
                });
            } else {
                let subFoldersSearchForCourse = this.courseInFolderSearch(folders, currentFolder);
                subFoldersSearchForCourse.push(folderToSearch);
                let searchCourseToReturn = [];
                for (let j = 0; j < subFoldersSearchForCourse.length; j++) {
                    searchCourseToReturn.push(...coursesToPrint.filter(coursesToPrint =>
                        coursesToPrint.folderid == subFoldersSearchForCourse[j].id));
                }
                return searchCourseToReturn;
            }
        }
    }

    getAllCoursesModel(): Course[]  {
        if(this.allCourses){
            return this.allCourses;
        }
        return [];
    }

    async getChoice() {
        try {
            const {data} = await http.get('/moodle/choices');
            if(data.length != 0) {
                this.lastcreation = data[0].lastcreation;
                this.todo = data[0].todo;
                this.tocome = data[0].tocome;
                this.coursestodosort = this.typeShow.filter(type => type.id == data[0].coursestodosort );
                this.coursestocomesort = this.typeShow.filter(type => type.id == data[0].coursestocomesort );
            }
            else
            {
                this.lastcreation = true;
                this.todo = true;
                this.tocome = true;
                this.coursestodosort = this.typeShow.filter(type => type.id == "doing" );
                this.coursestocomesort = this.typeShow.filter(type => type.id == "all" );
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
        }else if (view == 3){
            try {
                await http.put(`/moodle/choices/tocome`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice toCome function didn't work");
            }
        }else if (view == 4){
            try {
                await http.put(`/moodle/choices/coursestodosort`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice coursesToDo sorting function didn't work");
            }
        }else if (view == 5){
            try {
                await http.put(`/moodle/choices/coursestocomesort`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice coursesToCome sorting  function didn't work");
            }
        }
    }

    toJsonForMove(targetId : number){
        return {
            folderId: targetId,
            coursesId: this.allCourses.filter(course => course.select).map(course => course.courseid )
        }
    }

    async moveToFolder(targetId : number) {
        try {
            this.allCourses.filter(course => course.select).map(course => course.folderid = targetId);
            await http.put(`/moodle/courses/move`, this.toJsonForMove(targetId));
        } catch (e) {
            throw e;
        }
    }

    orderByField (fieldName) {
        if (fieldName === this.order.field) {
            this.order.desc = !this.order.desc;
        }
        else {
            this.order.desc = false;
            this.order.field = fieldName;
        }
    };

    isOrderedAsc (field) {
        return this.order.field === field && !this.order.desc;
    }
    isOrderedDesc (field) {
        return this.order.field === field && this.order.desc;
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
