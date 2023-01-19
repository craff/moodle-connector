import {Behaviours, model} from "entcore";

console.log('moodle behaviours loaded');

const moodleBehaviours = {
	resources: {
		read: {
			right: "fr-openent-moodle-controllers-ShareController|read"
		},
		contrib: {
			right: "fr-openent-moodle-controllers-ShareController|contrib"
		},
		manager: {
			right: "fr-openent-moodle-controllers-ShareController|shareSubmit"
		}
	},
	workflow: {
		createCourse: 'fr.openent.moodle.controllers.CourseController|create',
		delete: 'fr.openent.moodle.controllers.CourseController|delete',
		view: 'fr.openent.moodle.controllers.MoodleController|view',
		publish: 'fr.openent.moodle.controllers.PublishedController|publish',
		convert: 'fr.openent.moodle.controllers.MoodleController|convert',
		duplicate: 'fr.openent.moodle.controllers.DuplicateController|duplicate'
	},
	share: {
		overrideDefaultActions: ['moodle.contrib']
	}
};


Behaviours.register('moodle', {
	rights: moodleBehaviours,
	dependencies: {},
	/**
	 * Allows to set rights for behaviours.
	 */
	resource : function(resource) {
		let rightsContainer = resource;

		if (resource && !resource.myRights) {
			resource.myRights = {};
		}

		for (const behaviour in moodleBehaviours.resources) {
			if (model.me.hasRight(rightsContainer, moodleBehaviours.resources[behaviour]) ||
				model.me.userId === resource.owner.userId || model.me.userId === rightsContainer.owner.userId) {
				if (resource.myRights[behaviour] !== undefined) {
					resource.myRights[behaviour] = resource.myRights[behaviour] && moodleBehaviours.resources[behaviour];
				} else {
					resource.myRights[behaviour] = moodleBehaviours.resources[behaviour];
				}
			}
		}
		return resource;
	},

	/**
	 * Allows to define all rights to display in the share windows. Names are
	 * defined in the server part with
	 * <code>@SecuredAction(value = "xxxx.read", type = ActionType.RESOURCE)</code>
	 * without the prefix <code>xxx</code>.
	 */
	resourceRights: function () {
		return ['contrib', 'manager'];
	},

	loadResources: function () {
	},

	share: function() {
		return moodleBehaviours.share;
	}
});
