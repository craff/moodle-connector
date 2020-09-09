import {_, idiom, moment, notify, Rights, Shareable} from "entcore";
import http from "axios";
import {Mix} from "entcore-toolkit";
import {Folders} from "./Folder";
import {STATUS} from "../constantes";

export class Course implements Shareable {
    shared: any;
    owner: { userId: string; displayName: string };
    myRights: Rights<Course>;

    courseid: number;
    id: number;
    status: STATUS;
    fullname: string;
    summary: string;
    date: Date;
    auteur: Author;
    role: string;
    startdate: Date;
    enddate: Date;
    timemodified: number;
    enrolmentdate: number;
    categoryid: number;
    folderId: number;
    imageurl: string;
    type: string;
    typeA: string;
    progress: string;
    select: boolean;
    selectConfirm: boolean;
    masked: boolean;
    favorites: boolean;
    infoImg: {
        name: string;
        type: string;
        compatibleMoodle: boolean;
    };
    duplication: string;
    usernumber: number;
    disciplines : Array<string>;
    levels : Array<string>;
    plain_text: Array<string> = [];

    constructor() {
        this.type = "1";
        this.select = false;
        this.selectConfirm = false;
        this.myRights = new Rights<Course>(this);
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

    private toJSON() {
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
            folderId: this.folderId,
            id: this.courseid,
            nameImgUrl: this.infoImg ? this.infoImg.name.toLocaleLowerCase() : null,
        }
    }

    async create(): Promise<void> {
        try {
            const {data} = await http.post('/moodle/course', this.toJSON());
            this.courseid = data.moodleid;
            this.duplication = "non";
            await this.goTo();
        } catch (e) {
            this.fullname = undefined;
            this.summary = undefined;
            notify.error(idiom.translate("moodle.error.create.course"));
            throw e;
        }
    }

    async deleteDuplication() {
        try {
            await http.delete(`/moodle/courseDuplicate/${this.courseid}`);
        } catch (e) {
            notify.error("delete failed duplicate course didn't work");
            throw e;
        }
    }

    private async goTo(): Promise<void> {
        if (this.duplication == "non")
            window.open(`/moodle/course/${this.courseid}?scope=view`);
    }

    async setPreferences(preference: string) {

        if (preference == "masked") {
            this.masked = !this.masked;
        } else if (preference == "favorites") {
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
    publicBankCategoryId: number;
    deleteCategoryId: number;
    allByFolder: Course[];
    coursesShared: Course[];
    coursesPublished: Course[];
    coursesSharedToFollow: Course[];
    coursesByUser: Course[];
    isSynchronized: Boolean;
    allCourses: Course[];
    lastCreation: boolean;
    toDo: boolean;
    toCome: boolean;
    coursestodosort: any;
    coursestocomesort: any;
    coursesToDo: Course[];
    coursesToCome: Course[];
    coursesMyCourse: Course[];
    showCourses: Course[];
    folderId: number;
    categoryType: String;
    searchInput: any;
    order: any;
    typeShow: any;

    constructor() {
        this.allByFolder = [];
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
            field: "creationDate",
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

    toJsonForDelete() {
        return {
            coursesId: this.allCourses.filter(course => course.selectConfirm).map(course => course.courseid),
            categoryType: this.categoryType
        }
    }

    async coursesDelete() {
        try {
            await http.delete('/moodle/course', {data: this.toJsonForDelete()});
        } catch (e) {
            notify.error(idiom.translate("moodle.error.delete.course"));
            throw e;
        }
    }

    private coursesSelectedConfirmed(): Array<Course> {
        return this.allCourses.filter((course: Course): boolean => course.selectConfirm && course.select);
    }

    public async coursesDuplicate(folderId: number): Promise<void> {
        try {
            let coursesId: Array<number>;
            coursesId = this.coursesSelectedConfirmed().map((course: Course): number => course.id);
            const idsCoursesAndFolders: Object = {folderId: folderId, coursesId};
            await http.post('/moodle/course/duplicate', idsCoursesAndFolders);
        } catch (error) {
            notify.error(idiom.translate("moodle.error.duplicate.course"));
            throw error;
        }
    }

    public async getDuplicateCourse(): Promise<Course[]> {
        try {
            const {data} = await http.get('/moodle/duplicateCourses');
            return data
        } catch (error) {
            notify.error(idiom.translate("moodle.error.duplicate.course"));
            throw error;
        }
    }

    toJSON() {
        return {
            lastCreation: this.lastCreation,
            toDo: this.toDo,
            toCome: this.toCome,
            coursestodosort: this.coursestodosort[0].id,
            coursestocomesort: this.coursestocomesort[0].id
        }
    }

    async getCoursesByUser(userId: string) {
        try {
            let courses = await http.get(`/moodle/user/courses`);
            this.allCourses = Mix.castArrayAs(Course, courses.data.allCourses);
            let idAuditeur = courses.data.idAuditeur + "";
            let idEditingTeacher = courses.data.idEditingTeacher + "";
            let idStudent = courses.data.idStudent + "";
            this.publicBankCategoryId = courses.data.publicBankCategoryId;
            this.deleteCategoryId = courses.data.deleteCategoryId;

            _.each(this.allCourses, function (course) {
                course.id = course.courseid;
                course._id = course.courseid;
                course.owner = {
                    userId: course.auteur[0].entidnumber,
                    displayName: course.auteur[0].firstname + " " + course.auteur[0].lastname
                };
                if (course.summary == null) {
                    course.summary = "";
                }
            });
            this.coursesByUser = _.filter(this.allCourses, function (course) {
                return course.role === idAuditeur;
            });
            this.coursesShared = _.filter(this.allCourses, function (course) {
                return course.role === idEditingTeacher && course.auteur[0].entidnumber !== userId;
            });
            this.coursesSharedToFollow = _.filter(this.allCourses, function (course) {
                return course.role === idStudent && course.auteur[0].entidnumber !== userId;
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
            notify.error(idiom.translate("moodle.error.get.course"));
            this.isSynchronized = false;
            throw e;
        }
    }

    getLastCreation(): Course[] {
        return this.coursesByUser.filter((course: Course): boolean => course.duplication === 'non' &&
            course.categoryid != this.publicBankCategoryId)
            .sort((courseOne: any, courseTwo: any): number => courseTwo.date - courseOne.date)
            .slice(0, 5);
    }

    isTheFirst(courseFirst: Course) {
        return this.coursesToShow("coursesToDo").indexOf(courseFirst) == 0 || $(window).width() < 800;
    }

    coursesToShow(place: string) {
        let id;
        if (place == "coursesToDo") {
            if (this.coursesToDo) {
                this.showCourses = this.coursesToDo.filter(course => this.searchCoursesToDo(course));
                id = this.coursestodosort[0].id;
            }
        } else if (place == "coursesToCome") {
            if (this.coursesToCome) {
                this.showCourses = this.coursesToCome.filter(course => this.searchCoursesToCome(course));
                id = this.coursestocomesort[0].id;
            }
        } else if (place == "coursesMyCourse") {
            if (this.coursesMyCourse) {
                this.showCourses = this.coursesByUser.filter(course => this.searchMyCourses(course));
            }
        }
        if (id == "all")
            this.showCourses = _.filter(this.showCourses);
        else if (id == "doing")
            this.showCourses = _.filter(this.showCourses, function (course) {
                return (course.progress != "100%" && !(course.masked))
            });
        else if (id == "favorites")
            this.showCourses = _.filter(this.showCourses, function (course) {
                return (course.favorites);
            });
        else if (id == "finished")
            this.showCourses = _.filter(this.showCourses, function (course) {
                return (course.progress == "100%" && !(course.masked));
            });
        else if (id == "masked")
            this.showCourses = _.filter(this.showCourses, function (course) {
                return course.masked;
            });

        return this.showCourses;
    }

    orderCourses(coursesToOrder: Course[]) {
        return coursesToOrder.sort(
            (a, b) => {
                if (this.order.field == "creationDate") {
                    if (a.date > b.date)
                        if (this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.date < b.date)
                        if (this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if (this.order.field == "modificationDate") {
                    if (a.timemodified > b.timemodified)
                        if (this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.timemodified < b.timemodified)
                        if (this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if (this.order.field == "name") {
                    if (a.fullname.toLowerCase() < b.fullname.toLowerCase())
                        if (this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.fullname.toLowerCase() > b.fullname.toLowerCase())
                        if (this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if (this.order.field == "numberEnrolment") {
                    if (a.usernumber < b.usernumber)
                        if (this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.usernumber > b.usernumber)
                        if (this.order.desc)
                            return -1;
                        else
                            return 1;
                } else if (this.order.field == "achievement") {
                    if (a.progress.slice(0, a.progress.length - 1) < b.progress.slice(0, b.progress.length - 1))
                        if (this.order.desc)
                            return 1;
                        else
                            return -1;
                    if (a.progress.slice(0, a.progress.length - 1) > b.progress.slice(0, b.progress.length - 1))
                        if (this.order.desc)
                            return -1;
                        else
                            return 1;
                }
            }
        );
    }

    /*
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

    courseInFolderSearch(folders: Folders, currentFolder) {
        let folderToSearch = folders.listOfSubfolder.find(folder => folder.id == currentFolder);
        let subfolderSearchForCourse = [];
        for (let i = 0; i < folderToSearch.subfolder.length; i++) {
            subfolderSearchForCourse.push(folderToSearch.subfolder[i]);
            if (folderToSearch.subfolder[i].subfolder.length != 0) {
                subfolderSearchForCourse.push(...this.courseInFolderSearch(folders, folderToSearch.subfolder[i].id));
            }
        }
        return subfolderSearchForCourse;
    }

    getCourseInFolder(coursesToPrint: Course[], currentFolder, searching: string, folders: Folders) {
        let folderToSearch = folders.listOfSubfolder.find(folder => folder.id == currentFolder);
        if (folderToSearch) {
            if (folderToSearch.id == 0) {
                return _.filter(coursesToPrint, function (course) {
                    return !!searching ? (course.fullname.toLowerCase().includes(searching.toLowerCase()) ||
                        course.summary.toLowerCase().includes(searching.toLowerCase())) : course.folderId == 0;
                });
            } else {
                if (searching == '') {
                    return _.filter(coursesToPrint, function (course) {
                        return (course.folderId == folderToSearch.id)
                    });
                } else {
                    let subfolderSearchForCourse = this.courseInFolderSearch(folders, currentFolder);
                    subfolderSearchForCourse.push(folderToSearch);
                    let searchCourseToReturn = [];
                    for (let j = 0; j < subfolderSearchForCourse.length; j++) {
                        searchCourseToReturn.push(...coursesToPrint.filter(coursesToPrint =>
                            coursesToPrint.folderId == subfolderSearchForCourse[j].id));
                    }
                    return searchCourseToReturn;
                }
            }
        }
    }


    getAllCoursesModel(): Course[] {
        if (this.allCourses) {
            return this.allCourses;
        }
        return [];
    }

    async getChoice() {
        try {
            if(this.lastCreation == undefined || this.toDo == undefined || this.toCome == undefined ||
                this.coursestodosort == undefined || this.coursestocomesort == undefined) {
                const {data} = await http.get('/moodle/choices');
                if (data.length != 0) {
                    this.lastCreation = data[0].lastcreation;
                    this.toDo = data[0].todo;
                    this.toCome = data[0].tocome;
                    this.coursestodosort = this.typeShow.filter(type => type.id == data[0].coursestodosort);
                    this.coursestocomesort = this.typeShow.filter(type => type.id == data[0].coursestocomesort);
                } else {
                    this.lastCreation = true;
                    this.toDo = true;
                    this.toCome = true;
                    this.coursestodosort = this.typeShow.filter(type => type.id == "doing");
                    this.coursestocomesort = this.typeShow.filter(type => type.id == "all");
                }
            }
        } catch (e) {
            notify.error("Get Choice function didn't work");
        }
    }

    async setChoice(view: number) {
        if (view == 1) {
            try {
                await http.put(`/moodle/choices/lastCreation`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice lastCreation function didn't work");
            }
        } else if (view == 2) {
            try {
                await http.put(`/moodle/choices/toDo`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice toDo function didn't work");
            }
        } else if (view == 3) {
            try {
                await http.put(`/moodle/choices/toCome`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice toCome function didn't work");
            }
        } else if (view == 4) {
            try {
                await http.put(`/moodle/choices/coursestodosort`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice coursesToDo sorting function didn't work");
            }
        } else if (view == 5) {
            try {
                await http.put(`/moodle/choices/coursestocomesort`, this.toJSON());
            } catch (e) {
                notify.error("Set Choice coursesToCome sorting  function didn't work");
            }
        }
    }

    toJsonForMove(targetId: number) {
        return {
            folderId: targetId,
            coursesId: this.allCourses.filter(course => course.select).map(course => course.courseid)
        }
    }

    async moveToFolder(targetId: number) {
        try {
            this.allCourses.filter(course => course.select).map(course => course.folderId = targetId);
            await http.put(`/moodle/courses/move`, this.toJsonForMove(targetId));
        } catch (e) {
            throw e;
        }
    }

    orderByField(fieldName) {
        if (fieldName === this.order.field) {
            this.order.desc = !this.order.desc;
        } else {
            this.order.desc = false;
            this.order.field = fieldName;
        }
    };

    isOrderedAsc(field) {
        return this.order.field === field && !this.order.desc;
    }

    isOrderedDesc(field) {
        return this.order.field === field && this.order.desc;
    }
}

export class Author {
    entidnumber: string;
    firstname: string;
    lastname: string;

    toJson() {
        return {
            entidnumber: this.entidnumber,
            firstname: this.firstname,
            lastname: this.lastname,
        }
    }
}
