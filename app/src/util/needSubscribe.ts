import { showMessage } from "../dialog/message";
import { getCloudURL } from "../config/util/about";
import { json } from "stream/consumers";

export const needLogin = (tip = window.siyuan.languages.needLogin) => {
    // if (window.siyuan.user) {
    //     return false;
    // }
    // if (tip) {
    //     showMessage(tip);
    // }
    // return true;
    // if (tip) {
    //     showMessage(tip);
    // }
    return false;
};

export const needSubscribe = (tip = window.siyuan.languages._kernel[29]) => {
    return false;
};

export const isPaidUser = () => {
    return window.siyuan.user && (0 === window.siyuan.user.userSiYuanSubscriptionStatus || 1 === window.siyuan.user.userSiYuanOneTimePayStatus);
};
